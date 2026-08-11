package fr.lc4918.trailog.ui.routes

import java.text.Normalizer

/**
 * Recherche dans l'arborescence du menu lateral.
 *
 * A dix couches l'arbre se parcourt de l'oeil ; a deux cents, non. Ce qu'on cherche alors est presque
 * toujours un nom dont on ne se rappelle qu'un morceau, et rarement avec ses accents ni sa casse - d'ou
 * une comparaison qui ignore les deux. Sans cela, "vizille" ne trouverait pas "Vizille" et "col de
 * lecheres" ne trouverait pas "Col des Lecheres".
 *
 * Extrait du composable pour etre verifiable : une recherche qui ne trouve pas est une faute muette, elle
 * ressemble en tout point a une couche absente.
 */
object TreeSearch {

    /**
     * La forme comparable d'un texte : sans accent, sans casse, sans blancs de bord.
     *
     * La decomposition NFD separe une lettre accentuee de son accent, qui devient un caractere a part -
     * de la categorie "marque non espacante" - qu'il suffit ensuite de retirer. C'est la seule facon de
     * traiter d'un coup les huit langues de l'application, la ou une table de correspondance oublierait
     * toujours un caractere.
     */
    fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /**
     * Ce nom repond-il a cette recherche ?
     *
     * Sur un fragment quelconque du nom, et non sur son debut : on se souvient du "Ventoux" bien avant du
     * "2019-07-14 - Mont Ventoux" que l'appareil a nomme. Une recherche vide ne filtre rien.
     */
    fun matches(name: String, query: String): Boolean {
        val q = normalize(query)
        return q.isEmpty() || normalize(name).contains(q)
    }
}
