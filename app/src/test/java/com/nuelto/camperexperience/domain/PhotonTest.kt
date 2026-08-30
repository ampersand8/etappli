package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotonTest {

    /** Verbatim from GET /api?q=Lauterbrunnen — the village plus two objects at the station. */
    private val lauterbrunnen = """
        {"type":"FeatureCollection","features":[
        {"type":"Feature","properties":{"osm_type":"R","osm_id":1682529,"osm_key":"place",
        "osm_value":"village","type":"city","name":"Lauterbrunnen",
        "county":"Interlaken-Oberhasli administrative district","state":"Bern",
        "country":"Switzerland","countrycode":"CH","extra":{"admin_level":"8"},
        "extent":[7.8063083,46.6293943,8.0070611,46.4772577]},
        "geometry":{"type":"Point","coordinates":[7.9078016,46.5939043]}},
        {"type":"Feature","properties":{"osm_type":"N","osm_id":6037884620,
        "osm_key":"information","osm_value":"guidepost","type":"house","housenumber":"469k",
        "name":"Lauterbrunnen","street":"Station","locality":"Station","district":"Wengen",
        "city":"Lauterbrunnen","county":"Interlaken-Oberhasli administrative district",
        "state":"Bern","country":"Switzerland","postcode":"3822","countrycode":"CH"},
        "geometry":{"type":"Point","coordinates":[7.9080357,46.5983618]}},
        {"type":"Feature","properties":{"osm_type":"N","osm_id":6037884620,
        "osm_key":"railway","osm_value":"station","type":"house","housenumber":"469k",
        "name":"Lauterbrunnen","street":"Station","locality":"Station","district":"Wengen",
        "city":"Lauterbrunnen","county":"Interlaken-Oberhasli administrative district",
        "state":"Bern","country":"Switzerland","postcode":"3822","countrycode":"CH"},
        "geometry":{"type":"Point","coordinates":[7.9080357,46.5983618]}}]}
    """.trimIndent()

    private fun collection(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private val campingA =
        """{"properties":{"name":"Camping A","state":"Bern"},""" + point("7.9", "46.5") + "}"
    private val campingB =
        """{"properties":{"name":"Camping B","state":"Bern"},""" + point("7.8", "46.4") + "}"

    private fun point(lon: String, lat: String) =
        """"geometry":{"type":"Point","coordinates":[$lon,$lat]}"""

    @Test
    fun `parses the name, an address label and the coordinates in GeoJSON order`() {
        assertEquals(
            PlaceSuggestion("Lauterbrunnen", "Bern, Switzerland", LatLng(46.5939043, 7.9078016)),
            Photon.parse(lauterbrunnen).single(),
        )
    }

    @Test
    fun `the several OSM objects of one place collapse into one suggestion`() {
        // Village, guidepost and railway station are one row, the most relevant kept.
        assertEquals(1, Photon.parse(lauterbrunnen).size)
        // Different places sharing an address line still get a row each.
        val parsed = Photon.parse(
            collection(
                campingA,
                campingB,
            ),
        )
        assertEquals(listOf("Camping A", "Camping B"), parsed.map { it.name })
    }

    @Test
    fun `the name falls back from name to street to city, and nameless hits are dropped`() {
        val parsed = Photon.parse(
            collection(
                """{"properties":{"street":"Seestrasse","state":"Vaud"},${point("6.6", "46.5")}}""",
                """{"properties":{"city":"Sion"},${point("7.36", "46.23")}}""",
                """{"properties":{"postcode":"3000"},${point("7.45", "46.95")}}""",
            ),
        )
        assertEquals(listOf("Seestrasse", "Sion"), parsed.map { it.name })
    }

    @Test
    fun `a city-state does not repeat itself in the address line`() {
        val parsed = Photon.parse(
            collection(
                """{"properties":{"name":"Seestrasse","city":"Zurich","state":"Zurich",
                "country":"Switzerland"},""" + point("8.54", "47.37") + "}",
            ),
        )
        assertEquals("Zurich, Switzerland", parsed.single().label)
    }

    @Test
    fun `label components repeating the name are dropped and may leave it empty`() {
        val parsed = Photon.parse(
            collection(
                """{"properties":{"name":"Zermatt","city":"Zermatt","country":"Switzerland"},${point("7.74", "46.02")}}""",
                """{"properties":{"name":"Matterhorn"},${point("7.65", "45.97")}}""",
            ),
        )
        assertEquals("Switzerland", parsed[0].label)
        assertEquals("", parsed[1].label)
    }

    @Test
    fun `features without properties, geometry, a point or usable coordinates are skipped`() {
        val parsed = Photon.parse(
            collection(
                "42",
                """{"geometry":{"type":"Point","coordinates":[7.0,46.0]}}""",
                """{"properties":{"name":"A"}}""",
                """{"properties":{"name":"B"},"geometry":{"type":"LineString","coordinates":[[7.0,46.0]]}}""",
                """{"properties":{"name":"C"},"geometry":{"type":"Point","coordinates":"7.0,46.0"}}""",
                """{"properties":{"name":"D"},"geometry":{"type":"Point","coordinates":[]}}""",
                """{"properties":{"name":"F"},"geometry":{"type":"Point","coordinates":["x","y"]}}""",
            ),
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `a lone coordinate is not enough for a suggestion`() {
        assertTrue(Photon.parse(collection("""{"properties":{"name":"G"},"geometry":{"type":"Point","coordinates":[7.0]}}""")).isEmpty())
    }

    @Test
    fun `a property that is not a non-blank string counts as absent`() {
        val parsed = Photon.parse(
            collection("""{"properties":{"name":42,"street":"","city":"Fallback"},${point("7.0", "46.0")}}"""),
        )
        assertEquals(PlaceSuggestion("Fallback", "", LatLng(46.0, 7.0)), parsed.single())
    }

    @Test
    fun `an empty collection, an error body and a non-JSON body yield nothing`() {
        assertTrue(Photon.parse("""{"type":"FeatureCollection","features":[]}""").isEmpty())
        assertTrue(Photon.parse("""{"message":"q parameter is required"}""").isEmpty())
        assertTrue(Photon.parse("""{"lang":[{"message":"Language is not supported","value":"it"}]}""").isEmpty())
        assertTrue(Photon.parse("""{"features":{"type":"Feature"}}""").isEmpty())
        assertTrue(Photon.parse("<html><title>404 Not Found</title></html>").isEmpty())
        assertTrue(Photon.parse("[]").isEmpty())
        assertTrue(Photon.parse("").isEmpty())
    }

    @Test
    fun `duplicates are collapsed before the list is cut to five`() {
        // Eight features, four of them repeats: all five distinct places must survive,
        // which only holds while the dedup runs ahead of the cut.
        val many = listOf(1, 1, 2, 2, 3, 4, 5, 6)
            .map { """{"properties":{"name":"Camp $it"},${point("7.$it", "46.$it")}}""" }
        assertEquals(
            listOf("Camp 1", "Camp 2", "Camp 3", "Camp 4", "Camp 5"),
            Photon.parse(collection(*many.toTypedArray())).map { it.name },
        )
    }

    @Test
    fun `the url encodes the query and asks for more hits than it shows`() {
        val url = Photon.searchUrl("Camping Zürich Nord", near = null, language = "en")
        assertEquals(
            "https://photon.komoot.io/api?q=Camping+Z%C3%BCrich+Nord&limit=8&lang=en",
            url,
        )
        assertFalse(url.contains("lat="))
    }

    @Test
    fun `a bias adds a focus point wide enough for a day's drive`() {
        assertEquals(
            "https://photon.komoot.io/api?q=Thun&limit=8&lang=de&lat=46.95&lon=7.45&zoom=8",
            Photon.searchUrl("Thun", LatLng(46.95, 7.45), language = "de"),
        )
    }

    @Test
    fun `the language is clamped to what the public instance accepts`() {
        assertEquals("de", Photon.language("de"))
        assertEquals("fr", Photon.language("fr"))
        assertEquals("en", Photon.language("EN"))
        assertEquals("en", Photon.language("it"))
        assertEquals("en", Photon.language("rm"))
        assertEquals("en", Photon.language(""))
    }

    @Test
    fun `each kind of stop asks OSM for its own tags`() {
        assertEquals(listOf("tourism:camp_site"), Photon.preferredTags(StopKind.CAMPSITE))
        assertEquals(listOf("tourism:caravan_site"), Photon.preferredTags(StopKind.STELLPLATZ))
        assertEquals(listOf("highway:rest_area", "amenity:parking"), Photon.preferredTags(StopKind.FREE_CAMP))
        assertEquals(listOf("tourism:attraction"), Photon.preferredTags(StopKind.VISIT))
    }

    @Test
    fun `tags are appended to the url, encoded`() {
        assertEquals(
            "https://photon.komoot.io/api?q=grimsel&limit=8&lang=en&osm_tag=tourism%3Acamp_site",
            Photon.searchUrl("grimsel", null, "en", listOf("tourism:camp_site")),
        )
        assertEquals(
            "https://photon.komoot.io/api?q=g&limit=8&lang=en&lat=46.5&lon=7.9&zoom=8" +
                "&osm_tag=highway%3Arest_area&osm_tag=amenity%3Aparking",
            Photon.searchUrl("g", LatLng(46.5, 7.9), "en", listOf("highway:rest_area", "amenity:parking")),
        )
    }
}
