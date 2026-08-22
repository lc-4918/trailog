package fr.lc4918.trailog.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La CHAINE des migrations, et non chaque maillon.
 *
 * `MigrationsTest` rejoue chaque migration sur un SQLite reel et verifie qu'elle fait ce qu'elle annonce.
 * Il ne dit rien du cas qui detruit vraiment des donnees : une migration **absente**.
 *
 * Le defaut vise est precis et facile a commettre seul. On ajoute une colonne a `SettingsEntity`, on ecrit
 * son SQL, on incremente `version`, et l'on oublie de declarer l'objet `Migration` ou de l'enregistrer.
 * Rien ne le signale : le code compile, les 55 autres tests passent, l'application demarre sur une base
 * neuve. Elle ne se casse que chez quelqu'un qui avait deja des couches importees.
 *
 * Room se rabattait alors sur `fallbackToDestructiveMigration`, qui supprime toutes les tables et les
 * recree. Le repli est desormais borne aux versions anterieures a la premiere migration ecrite
 * ([AppDatabase.OLDEST_SUPPORTED]) : partout ailleurs, un trou fait echouer l'ouverture. Un plantage au
 * demarrage se corrige par une mise a jour ; des couches effacees ne reviennent pas.
 *
 * Ces tests-ci sont ce qui rend ce durcissement tenable : ils font echouer la CI avant la mise en ligne,
 * plutot que le telephone apres.
 */
class MigrationChainTest {

    private val declaree = DB_VERSION
    private val migrations = AppDatabase.ALL_MIGRATIONS

    /** La chaine part bien de la plus ancienne version qu'on declare savoir migrer. */
    @Test fun `la chaine commence a la plus ancienne version supportee`() {
        val depart = migrations.minOf { it.startVersion }
        assertEquals(
            "la premiere migration doit partir de OLDEST_SUPPORTED, sinon le repli destructeur " +
                "couvre une version qu'on croyait migrer",
            AppDatabase.OLDEST_SUPPORTED, depart,
        )
    }

    /**
     * Le test central : aucun trou entre la plus ancienne version et celle que declare `@Database`.
     *
     * C'est exactement ce qu'une migration oubliee produirait, et c'est ce qui detruisait des donnees.
     */
    @Test fun `aucune version ne manque entre la plus ancienne et la declaree`() {
        val pas = migrations.associateBy { it.startVersion }
        val manquantes = (AppDatabase.OLDEST_SUPPORTED until declaree).filterNot { it in pas }
        assertTrue(
            "migrations manquantes depuis les versions $manquantes : la base d'un utilisateur qui " +
                "en porte une ne pourra pas s'ouvrir",
            manquantes.isEmpty(),
        )
    }

    /** Chaque migration avance d'exactement une version : un saut cacherait un palier non teste. */
    @Test fun `chaque migration avance d'un seul cran`() {
        val sauts = migrations.filter { it.endVersion != it.startVersion + 1 }
            .map { "${it.startVersion} vers ${it.endVersion}" }
        assertTrue("migrations qui sautent des versions : $sauts", sauts.isEmpty())
    }

    /**
     * La derniere migration atteint la version declaree.
     *
     * Attrape l'oubli symetrique du precedent : on ecrit et on enregistre la migration, mais on oublie
     * d'incrementer `version`. Room ne migre alors jamais, et la colonne ajoutee n'existe pas a l'execution.
     */
    @Test fun `la chaine atteint la version declaree`() {
        assertEquals(
            "la derniere migration doit mener a la version de @Database",
            declaree, migrations.maxOf { it.endVersion },
        )
    }

    /** Deux migrations ne peuvent pas partir de la meme version : Room en choisirait une, et rien ne dit
     *  laquelle. */
    @Test fun `aucune version de depart n'est declaree deux fois`() {
        val doublons = migrations.groupBy { it.startVersion }.filterValues { it.size > 1 }.keys
        assertTrue("versions de depart declarees plusieurs fois : $doublons", doublons.isEmpty())
    }

    /** Le compte, pour que l'ajout d'une migration se voie dans le diff du test comme dans celui du code. */
    @Test fun `le nombre de migrations correspond a l'etendue couverte`() {
        assertEquals(declaree - AppDatabase.OLDEST_SUPPORTED, migrations.size)
    }
}
