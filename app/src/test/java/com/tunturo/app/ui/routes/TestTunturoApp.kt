package com.tunturo.app.ui.routes

import com.tunturo.app.TunturoApp

/**
 * La vraie application, moins les trois choses qu'un test JVM ne peut pas subir.
 *
 * Le moteur natif de la carte, qui ne se charge pas sur la JVM. Et le demarrage en tache de fond, dont
 * le semis ecrit les reglages a un moment qu'on ne choisit pas : le test appelle `ensureSeed` lui-meme,
 * puis pose les siens, sans course. Et la verification des zones d'itineraire, qui interroge le serveur.
 *
 * Tout le reste est de production : le depot, la base, les DAO, les reglages.
 */
class TestTunturoApp : TunturoApp() {
    override fun initMapEngine() { /* pas de bibliotheques natives sur la JVM */ }
    override fun startBackgroundWork() { /* le test decide quand semer */ }
    override fun checkZoneUpdates() { /* pas de serveur sous test */ }
}
