package com.tunturo.app.ui.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un tap sur le parcours, son depart ou son arrivee : la bande se redeploie, et la carte se cadre sur
 * tout le trajet (une demande de cadrage par tap).
 */
class ShowWholeRouteTest {

    @Test fun `planificateur ferme, rien ne se passe`() {
        val state = RoutePlannerState()
        state.showWholeRoute()
        assertFalse(state.open)
        assertEquals(0, state.frameRequest)
    }

    @Test fun `bande rangee, elle se redeploie et le cadrage est demande`() {
        val state = RoutePlannerState()
        state.openPlanner()
        state.collapse(true)
        state.showWholeRoute()
        assertFalse("bande redeployee", state.collapsed)
        assertEquals(1, state.frameRequest)
    }

    /** Le tap ne sert qu'a regarder : meme avec une etape sur la position actuelle, rien ne se recalcule. */
    @Test fun `redeployer la bande ne relance pas le calcul`() {
        val state = RoutePlannerState()
        state.openPlanner()
        state.choose(state.steps.first(), StepTarget.CurrentPosition)
        state.collapse(true)
        val revisionBefore = state.revision
        state.showWholeRoute()
        assertFalse("bande redeployee", state.collapsed)
        assertEquals("aucun recalcul", revisionBefore, state.revision)
    }

    /** Un trajet parti de la position actuelle, calcule au point (45.0, 6.0) en (lat, lon). */
    private fun routeFromCurrentPosition(): RoutePlannerState {
        val state = RoutePlannerState()
        state.openPlanner()
        state.choose(state.steps.first(), StepTarget.CurrentPosition)
        state.rememberCurrentPosition(45.0 to 6.0)
        state.collapse(true)
        return state
    }

    @Test fun `deplace de plus de 50 m, le trajet se refait depuis la position actuelle`() {
        val state = routeFromCurrentPosition()
        val revisionBefore = state.revision
        state.showWholeRoute(45.001 to 6.0) // environ 111 m plus au nord
        assertTrue("recalcul demande", state.revision != revisionBefore)
    }

    @Test fun `deplace de moins de 50 m, le trajet reste tel quel`() {
        val state = routeFromCurrentPosition()
        val revisionBefore = state.revision
        state.showWholeRoute(45.0002 to 6.0) // environ 22 m
        assertEquals(revisionBefore, state.revision)
    }

    @Test fun `position inconnue, le trajet reste tel quel`() {
        val state = routeFromCurrentPosition()
        val revisionBefore = state.revision
        state.showWholeRoute(null)
        assertEquals(revisionBefore, state.revision)
    }

    @Test fun `bande ouverte, chaque tap redemande le cadrage`() {
        val state = RoutePlannerState()
        state.openPlanner()
        state.showWholeRoute()
        state.showWholeRoute()
        assertFalse(state.collapsed)
        assertEquals(2, state.frameRequest)
    }
}
