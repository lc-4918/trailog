package com.tunturo.app.ui.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.tunturo.app.R
import com.tunturo.app.ui.components.MapController
import com.tunturo.app.ui.planner.RoutePlannerState
import com.tunturo.app.ui.theme.TunturoIcons
import kotlin.math.roundToInt

private data class StepMenuAnchor(val stepId: Long, val x: Int, val y: Int)

/**
 * Ce que fait la carte des pastilles d'etapes : un tap ouvre leur menu (arrivee, suppression), un appui
 * bref les laisse deplacer, et la pastille deposee redonne un point a l'etape - le parcours se recalcule.
 *
 * @param coordinatesLabel le libelle provisoire d'un point montre du doigt : ses coordonnees, le temps que
 *   le geocodeur en rende l'adresse (cf. RoutePlannerState.relocateStep).
 */
@Composable
internal fun PlannerStepMenu(
    controller: MapController,
    planner: RoutePlannerState,
    coordinatesLabel: (lon: Double, lat: Double) -> String,
) {
    var anchor by remember { mutableStateOf<StepMenuAnchor?>(null) }
    LaunchedEffect(controller) {
        controller.onPickPlannerStep = { stepId ->
            controller.stepScreenPosition(stepId)?.let {
                anchor = StepMenuAnchor(stepId, it.x.roundToInt(), it.y.roundToInt())
            }
        }
        controller.onStepDropped = { stepId, lon, lat ->
            planner.relocateStep(stepId, lon, lat, coordinatesLabel(lon, lat))
        }
    }
    val open = anchor ?: return
    Box(Modifier.offset { IntOffset(open.x, open.y) }) {
        DropdownMenu(expanded = true, onDismissRequest = { anchor = null }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.planner_step_set_end)) },
                leadingIcon = { Icon(Icons.Filled.Flag, null) },
                onClick = { anchor = null; planner.makeEnd(open.stepId) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(TunturoIcons.Trash, null) },
                onClick = { anchor = null; planner.removeStepById(open.stepId) },
            )
        }
    }
}
