package fr.pilmil.sillon

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.FrenchFrostCatalog
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.CompanionRelation
import fr.pilmil.sillon.model.EvidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    @Test
    fun testCatalogSizeAndDiversity() {
        val all = VegetableCatalog.ALL_VEGETABLES
        assertTrue("Le catalogue doit contenir au moins 40 plantes (reçu: ${all.size})", all.size >= 40)

        // Vérifier la présence des familles botaniques essentielles
        val families = all.map { it.family }.toSet()
        assertTrue("Doit contenir des Solanacées", families.contains(BotanicalFamily.SOLANACEAE))
        assertTrue("Doit contenir des Fabacées", families.contains(BotanicalFamily.FABACEAE))
        assertTrue("Doit contenir des Brassicacées", families.contains(BotanicalFamily.BRASSICACEAE))
        assertTrue("Doit contenir des Apiacées", families.contains(BotanicalFamily.APIACEAE))
        assertTrue("Doit contenir des Alliacées", families.contains(BotanicalFamily.ALLIACEAE))
        assertTrue("Doit contenir des Cucurbitacées", families.contains(BotanicalFamily.CUCURBITACEAE))
        assertTrue("Doit contenir des Chénopodiacées", families.contains(BotanicalFamily.CHENOPODIACEAE))
        assertTrue("Doit contenir des Astéracées", families.contains(BotanicalFamily.ASTERACEAE))
        assertTrue("Doit contenir des Lamiacées", families.contains(BotanicalFamily.LAMIACEAE))

        // Vérifier les fleurs compagnes
        val flowerIds = listOf("tagete", "bourrache", "capucine", "phacelie", "souci")
        flowerIds.forEach { id ->
            assertNotNull("La fleur compagne '$id' doit être présente dans le catalogue", VegetableCatalog.getById(id))
        }
    }

    @Test
    fun testAllCompanionReferencesAreValid() {
        val catalogMap = VegetableCatalog.BY_ID

        for (vegetable in VegetableCatalog.ALL_VEGETABLES) {
            // Vérifier toutes les bonnes associations
            for (companion in vegetable.goodCompanions) {
                val targetId = companion.targetPlantId
                assertTrue(
                    "Dans '${vegetable.id}', le bon compagnon '$targetId' doit exister dans le catalogue",
                    catalogMap.containsKey(targetId)
                )
                assertNotNull("Le type de bénéfice ne doit pas être nul", companion.benefitType)
                assertFalse("La justification ne doit pas être vide", companion.rationale.isBlank())
            }

            // Vérifier tous les mauvais compagnons
            for (companion in vegetable.badCompanions) {
                val targetId = companion.targetPlantId
                assertTrue(
                    "Dans '${vegetable.id}', le mauvais compagnon '$targetId' doit exister dans le catalogue",
                    catalogMap.containsKey(targetId)
                )
                assertNotNull("Le type de risque ne doit pas être nul", companion.riskType)
                assertFalse("La justification ne doit pas être vide", companion.rationale.isBlank())
            }
        }
    }

    private fun allRelations(): List<Pair<String, CompanionRelation>> =
        VegetableCatalog.ALL_VEGETABLES.flatMap { v -> (v.goodCompanions + v.badCompanions).map { v.id to it } }

    @Test
    fun everyRelationHasAnEvidenceLevelAndEveryEtayeRelationHasASource() {
        for ((owner, relation) in allRelations()) {
            assertNotNull("Relation $owner vers ${relation.targetPlantId} : niveau de preuve manquant", relation.evidence)
            if (relation.evidence == EvidenceLevel.ETAYE) {
                assertFalse(
                    "Relation étayée $owner vers ${relation.targetPlantId} : la source est obligatoire",
                    relation.source.isNullOrBlank(),
                )
            }
        }
    }

    @Test
    fun relationTotalsPerEvidenceLevelMatchTheSourcesDocument() {
        // docs/sources-compagnonnage.md : 255 relations au départ (37 étayées, 84 plausibles, 113 traditions, 21 contredites),
        // 30 retirées, puis 2 doublons supprimés par la fusion tagete / oeillet_d_inde (2 étayées).
        val byLevel = allRelations().groupingBy { it.second.evidence }.eachCount()
        assertEquals(35, byLevel[EvidenceLevel.ETAYE])
        assertEquals(84, byLevel[EvidenceLevel.PLAUSIBLE])
        assertEquals(104, byLevel[EvidenceLevel.TRADITION])
        assertEquals(223, allRelations().size)
        assertEquals(141, VegetableCatalog.ALL_VEGETABLES.sumOf { it.goodCompanions.size })
        assertEquals(82, VegetableCatalog.ALL_VEGETABLES.sumOf { it.badCompanions.size })
    }

    @Test
    fun relationsRemovedAfterReviewAreGone() {
        fun has(from: String, to: String) =
            VegetableCatalog.getById(from)!!.let { v -> (v.goodCompanions + v.badCompanions).any { it.targetPlantId == to } }
        assertFalse(has("capucine", "chou_pomme"))
        assertFalse(has("fenouil", "poireau"))
        assertFalse(has("aneth", "carotte"))
        assertFalse(has("carotte", "aneth"))
        for (cucurbit in listOf("courgette", "concombre", "melon", "pasteque")) {
            assertFalse(has("pomme_de_terre", cucurbit))
            assertFalse(has(cucurbit, "pomme_de_terre"))
        }
        // Les aromatiques « répulsives » contre les ravageurs des choux et de la carotte n'ont pas été confirmées par les essais.
        assertFalse(has("chou_pomme", "menthe"))
        assertFalse(has("romarin", "carotte"))
    }

    @Test
    fun tageteAndOeilletDIndeAreOnePlant() {
        assertNotNull(VegetableCatalog.getById("tagete"))
        assertFalse(VegetableCatalog.ALL_VEGETABLES.any { it.id == "oeillet_d_inde" })
        assertEquals("tagete", VegetableCatalog.canonicalId("oeillet_d_inde"))
        assertEquals("tagete", VegetableCatalog.getById("oeillet_d_inde")?.id)
        assertEquals("tomate", VegetableCatalog.canonicalId("tomate"))
        // Plus aucune relation ne vise l'ancien identifiant, et tagete n'apparaît qu'une fois dans la liste de la tomate.
        assertFalse(allRelations().any { it.second.targetPlantId == "oeillet_d_inde" })
        assertEquals(1, VegetableCatalog.getById("tomate")!!.goodCompanions.count { it.targetPlantId == "tagete" })
    }

    @Test
    fun traditionAndPlausibleRelationsAreNotWordedAsFacts() {
        val tradition = CompanionRelation(
            "carotte", "Bloque les nodosités", fr.pilmil.sillon.model.CompanionRiskType.ALLELOPATHY, EvidenceLevel.TRADITION,
        )
        val note = tradition.noteFor("Carotte")
        assertTrue(note, note.contains("tradition", ignoreCase = true))
        assertTrue(note, note.endsWith(EvidenceLevel.TRADITION.frenchLabel))
        val etaye = CompanionRelation(
            "tomate", "Moins d'aleurodes", fr.pilmil.sillon.model.CompanionBenefitType.PEST_REPELLENT, EvidenceLevel.ETAYE, "Auteur 2016",
        )
        assertTrue(etaye.noteFor("Tomate").endsWith("Étayé par essais (Auteur 2016)"))
        for (v in VegetableCatalog.ALL_VEGETABLES) {
            assertFalse("Un texte « éprouvé » ou « puissant » sans essai : ${v.id}", (v.goodCompanions + v.badCompanions).any {
                it.rationale.contains("éprouvé") || it.rationale.contains("puissant")
            })
        }
    }

    @Test
    fun testVegetableScientificFieldsIntegrity() {
        for (v in VegetableCatalog.ALL_VEGETABLES) {
            assertFalse("L'ID ne doit pas être vide", v.id.isBlank())
            assertFalse("Le nom ne doit pas être vide pour ${v.id}", v.name.isBlank())
            assertFalse("Le nom latin ne doit pas être vide pour ${v.id}", v.latinName.isBlank())
            assertTrue("La densité en carré potager doit être > 0 pour ${v.id}", v.squareFootDensity in listOf(1, 2, 4, 8, 9, 16))
            assertTrue("L'espacement dans le rang doit être > 0 pour ${v.id}", v.spacingInRowCm > 0)
            assertTrue("L'espacement entre rangs doit être > 0 pour ${v.id}", v.spacingBetweenRowsCm > 0)
            assertTrue("La température de germination min doit être réaliste pour ${v.id}", v.tempGerminationMin in 0..25)
            assertTrue("La plage de températures optimales doit être cohérente pour ${v.id}", v.tempOptimalMin <= v.tempOptimalMax)
            assertTrue("La durée de levée doit être positive pour ${v.id}", v.daysToEmerge.first > 0 && v.daysToEmerge.first <= v.daysToEmerge.last)
            assertTrue("La durée avant récolte doit être positive pour ${v.id}", v.daysToHarvest.first > 0 && v.daysToHarvest.first <= v.daysToHarvest.last)
            assertTrue("La durée de récolte doit être > 0 pour ${v.id}", v.harvestDurationWeeks > 0)
            assertFalse("Le conseil agronomique ne doit pas être vide pour ${v.id}", v.advice.isBlank())
        }
    }

    @Test
    fun testIndoorSuitableVegetablesSelection() {
        val indoorVegs = VegetableCatalog.ALL_VEGETABLES.filter { it.indoorSuitable }.map { it.id }.toSet()
        assertTrue("Au moins 15 variétés doivent être compatibles intérieur", indoorVegs.size >= 15)
        for (expected in listOf("basilic", "menthe", "ciboulette", "persil", "radis", "roquette", "laitue")) {
            assertTrue("'$expected' devrait être compatible intérieur", expected in indoorVegs)
        }
        for (excluded in listOf("pomme_de_terre", "mais_doux", "courge_butternut", "asperge")) {
            assertFalse("'$excluded' ne devrait pas être marqué compatible intérieur", excluded in indoorVegs)
        }
    }

    @Test
    fun testFrenchFrostCatalogCoverage() {
        val departments = FrenchFrostCatalog.DEPARTMENTS
        assertTrue("Le catalogue des gelées doit contenir au moins 96 départements (reçu: ${departments.size})", departments.size >= 96)

        // Vérifier les départements métropolitains de référence (ex: 75 Paris, 13 Bouches-du-Rhône, 67 Bas-Rhin, 29 Finistère)
        val paris = FrenchFrostCatalog.getByCode("75")
        assertNotNull(paris)
        assertTrue(paris!!.medianLastFrost.month in 3..5)

        val marseille = FrenchFrostCatalog.getByCode("13")
        assertNotNull(marseille)
        assertEquals(3, marseille!!.medianLastFrost.month) // Mars en zone méditerranéenne

        val strasbourg = FrenchFrostCatalog.getByCode("67")
        assertNotNull(strasbourg)
        assertEquals(5, strasbourg!!.medianLastFrost.month) // Mai en Alsace

        // Vérifier les DOM-TOM
        val reunion = FrenchFrostCatalog.getByCode("974")
        assertNotNull(reunion)
    }

    @Test
    fun testDiseaseCatalogIntegrity() {
        val diseases = DiseaseCatalog.ALL_DISEASES
        assertTrue("Le catalogue des maladies doit contenir au moins 10 affections clés", diseases.size >= 10)

        val requiredDiseases = listOf(
            "mildiou",
            "oidium",
            "cul_noir",
            "pucerons",
            "altises",
            "rouille",
            "limaces",
            "mouche_carotte",
            "teigne_poireau",
            "pieride_chou",
        )

        for (id in requiredDiseases) {
            val disease = DiseaseCatalog.getById(id)
            assertNotNull("La maladie ou ravageur '$id' doit être catalogué", disease)
            assertTrue("Les organes atteints ne doivent pas être vides pour '$id'", disease!!.affectedOrgans.isNotEmpty())
            assertTrue("Les symptômes doivent être documentés pour '$id'", disease.symptoms.isNotEmpty())
            assertTrue("Les causes doivent être documentées pour '$id'", disease.causes.isNotEmpty())
            assertTrue("Les remèdes bio doivent être documentés pour '$id'", disease.organicRemedies.isNotEmpty())
        }

        // Vérifier l'unicité et la complétude de l'ensemble du catalogue maladies & légumes
        assertEquals(diseases.size, diseases.map { it.id }.toSet().size)
        for (d in diseases) {
            assertFalse("Nom vide pour ${d.id}", d.name.isBlank())
            assertTrue("Organes vides pour ${d.id}", d.affectedOrgans.isNotEmpty())
            assertTrue("Symptômes vides pour ${d.id}", d.symptoms.isNotEmpty())
            assertTrue("Causes vides pour ${d.id}", d.causes.isNotEmpty())
            assertTrue("Remèdes vides pour ${d.id}", d.organicRemedies.isNotEmpty())
        }
        assertEquals(null, DiseaseCatalog.getById("affection_inexistante"))
        assertEquals(
            VegetableCatalog.ALL_VEGETABLES.size,
            VegetableCatalog.ALL_VEGETABLES.map { it.id }.toSet().size,
        )
        assertEquals(null, VegetableCatalog.getById("plante_inexistante"))
    }
}
