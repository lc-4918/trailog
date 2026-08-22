package fr.lc4918.trailog.ui.routes

/**
 * Ou un element vient se poser quand on le lache dans une arborescence, et dans quel ordre se retrouve
 * la fratrie qui l'accueille.
 *
 * **Deux arbres, un seul calcul.** Le menu lateral range des dossiers et des couches ; l'ecran des
 * reglages range des dossiers, des fonds et des composites. Les identifiants different - un entier d'un
 * cote, une chaine de l'autre - mais la regle de depose est rigoureusement la meme, et elle etait ecrite
 * deux fois, mot pour mot. Une correction sur l'une aurait laisse l'autre en place.
 *
 * **Pur, et sans Android.** C'est ce qui permet de le verrouiller par des tests : le reste du
 * reordonnancement n'est qu'ecriture en base, et se lit d'un coup d'oeil une fois ce calcul-ci mis de cote.
 */

/** Position de depose lors d'un glisser-deposer : avant ou apres un voisin, ou dedans (dossier cible). */
enum class DropPosition { BEFORE, INTO, AFTER }

/**
 * L'ordre de la fratrie d'accueil apres la depose.
 *
 * @param siblings la fratrie qui accueille, **l'element deplace deja retire** - il peut en venir, et
 *   s'y trouver deux fois donnerait deux rangs au meme objet.
 * @param moved l'element depose.
 * @param targetIndex le rang de la cible dans [siblings], ou **-1 si elle ne s'y trouve pas**. Le cas
 *   n'est pas theorique : on lache sur un dossier ferme, ou sur un voisin qui vient d'etre supprime.
 *   L'element va alors a la fin, ce qui est la seule reponse qui ne perde rien.
 */
fun <T> droppedOrder(
    siblings: List<T>,
    moved: T,
    targetIndex: Int,
    position: DropPosition,
): List<T> {
    val insertAt = when {
        position == DropPosition.INTO -> siblings.size
        targetIndex < 0 -> siblings.size
        position == DropPosition.BEFORE -> targetIndex
        else -> targetIndex + 1
    }
    return siblings.toMutableList().apply { add(insertAt, moved) }
}
