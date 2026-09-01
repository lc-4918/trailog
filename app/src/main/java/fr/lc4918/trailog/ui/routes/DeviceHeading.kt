package fr.lc4918.trailog.ui.routes

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.R
import fr.lc4918.trailog.location.LocationHub

/**
 * L'orientation du telephone, pour les symboles de position qui en portent une (les deux fleches).
 *
 * Trois fonctions de trigonometrie, sans rapport avec l'ecran qui les appelle : c'est le genre de calcul
 * qui se relit et se teste bien mieux seul.
 */

/**
 * Rotation de l'ECRAN par rapport a l'orientation naturelle de l'appareil (cf. [azimuthDegrees]).
 *
 * Relue a chaque changement de configuration, seul signal dont dispose Compose pour dire qu'on vient de
 * tourner le telephone : le capteur, lui, parle toujours dans le repere de l'appareil, pas dans celui de
 * l'ecran, et la difference entre les deux est exactement ce quart de tour.
 */
@Composable
internal fun rememberDisplayRotation(): Int {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ctx.display!!.rotation
            else @Suppress("DEPRECATION")
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }.getOrDefault(Surface.ROTATION_0)
    }
}

/**
 * Cap du telephone, en degres depuis le nord magnetique, tire du vecteur de rotation du systeme.
 *
 * Le repere du capteur est celui de l'APPAREIL dans son orientation naturelle ; [displayRotation] le
 * ramene a celui de l'ecran tel qu'il est tenu, faute de quoi la fleche serait a un quart de tour de la
 * verite des qu'on passe en paysage.
 *
 * Le vecteur est tronque a ses quatre premieres composantes : certains appareils en publient cinq, que
 * getRotationMatrixFromVector refuse.
 */
internal fun azimuthDegrees(rotationVector: FloatArray, displayRotation: Int): Float {
    val v = if (rotationVector.size > 4) rotationVector.copyOf(4) else rotationVector
    val m = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(m, v)
    val (axisX, axisY) = when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
    val screen = FloatArray(9)
    SensorManager.remapCoordinateSystem(m, axisX, axisY, screen)
    val orientation = FloatArray(3)
    SensorManager.getOrientation(screen, orientation)
    return Math.toDegrees(orientation[0].toDouble()).toFloat()
}

/**
 * Ecoute le capteur d'orientation tant que [active], et rend le cap en degres depuis le nord VRAI.
 *
 * Le capteur n'est ecoute que quand la position est affichee ET que le symbole a une direction a montrer :
 * une boussole tourne en permanence, elle n'a pas a le faire pour une puce ronde.
 *
 * Le vecteur de rotation plutot que l'accelerometre et le magnetometre bruts : le systeme en tire deja une
 * orientation fusionnee et lissee, la ou recombiner les deux a la main donne une fleche qui tremble.
 *
 * [position] sert a corriger la declinaison magnetique - l'ecart entre le nord de la boussole et celui de
 * la carte, qui depend du lieu et atteint plusieurs degres en Europe.
 *
 * **La boussole n'est PAS la premiere source du cap affiche.** Des qu'on avance, c'est celui du
 * deplacement qui s'impose (cf. [HeadingFusion]) : le telephone pointe ou son support le tient, et la
 * fleche s'affichait donc en biais sur la trace suivie et tournait sur elle-meme des qu'on prenait
 * l'appareil en main. La boussole ne reprend la main qu'a l'arret, ou elle est la seule a dire quelque
 * chose - et lissee, car c'est la qu'elle tremble le plus.
 *
 * @param fix la derniere mesure du capteur : sa vitesse et son cap decident laquelle des deux sources
 *   parle. Null tant qu'aucune position n'est arrivee, la boussole repond alors seule.
 */
@Composable
internal fun HeadingEffect(
    active: Boolean,
    position: Pair<Double, Double>?,
    fix: LocationHub.Fix?,
    onHeading: (Float) -> Unit,
) {
    val ctx = LocalContext.current
    val declination = remember(position) {
        // Recalculee a chaque position recue, ce qui la laisse hors de la boucle du capteur : elle ne varie
        // pas d'un pas a l'autre.
        position?.let { (la, lo) ->
            GeomagneticField(la.toFloat(), lo.toFloat(), 0f, System.currentTimeMillis()).declination
        } ?: 0f
    }
    val currentDeclination by rememberUpdatedState(declination)
    val current = rememberUpdatedState(onHeading)
    val displayRotation = rememberDisplayRotation()
    // La mesure du moment, relue par l'ecouteur sans le relancer : le capteur d'orientation parle toutes
    // les 60 ms, le GPS toutes les 2 s, et redemarrer l'ecoute a chaque position couterait un
    // desabonnement pour rien.
    val currentFix = rememberUpdatedState(fix)
    /*
     * Le cap AFFICHE, garde d'une mesure a l'autre.
     *
     * Hors de l'ecouteur, qui est recree a chaque rotation de l'ecran : le garder dedans repartait de zero
     * et faisait balayer tout le cadran a la fleche au moment ou l'on tourne le telephone.
     */
    val affiche = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(active, displayRotation) {
        val sensors = (ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.takeIf { active }
        // Aucun capteur d'orientation : la fleche reste alors pointee au nord, ce que fait deja tout le
        // reste de la carte - un appareil sans boussole n'a rien d'autre a montrer.
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensors == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val deg = azimuthDegrees(e.values, displayRotation) + currentDeclination
                val boussole = HeadingFusion.normalize(deg)
                val f = currentFix.value
                val heading = HeadingFusion.heading(
                    previous = affiche.value,
                    compassDeg = boussole,
                    speedMps = f?.speedMps,
                    gpsBearingDeg = f?.bearingDeg,
                ) ?: return
                val precedent = affiche.value
                affiche.value = heading
                // Sous le degre, le symbole ne bougerait pas d'un pixel : on epargne a MapLibre un redessin
                // par mesure, a 60 ms d'intervalle.
                if (precedent != null && HeadingFusion.gap(heading, precedent) < 1f) return
                current.value(heading)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
    /*
     * Le cap du deplacement s'applique aussi SANS attendre le capteur d'orientation.
     *
     * Deux raisons : un appareil sans boussole n'aurait jamais rien pose du tout, l'ecouteur ci-dessus ne
     * s'installant pas ; et meme avec, faire attendre une position fraiche jusqu'au prochain tic du
     * magnetometre serait mettre la fleche en retard sur le virage qu'on vient de prendre.
     */
    LaunchedEffect(active, fix?.bearingDeg, fix?.speedMps) {
        if (!active) return@LaunchedEffect
        val cap = HeadingFusion.travelBearing(fix?.speedMps, fix?.bearingDeg) ?: return@LaunchedEffect
        affiche.value = cap
        current.value(cap)
    }
}
