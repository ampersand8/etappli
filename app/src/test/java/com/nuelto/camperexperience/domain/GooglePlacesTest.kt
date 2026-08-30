package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlacesTest {

    private fun place(
        id: String = "ChIJ1",
        name: String? = "Camping Delta",
        address: String? = "Via Respini 7, 6600 Locarno, Switzerland",
        location: String? = """"location":{"latitude":46.1712,"longitude":8.7936}""",
    ): String {
        val parts = buildList {
            add(""""id":"$id"""")
            name?.let { add(""""displayName":{"text":"$it","languageCode":"en"}""") }
            address?.let { add(""""formattedAddress":"$it"""") }
            location?.let { add(it) }
        }
        return "{${parts.joinToString(",")}}"
    }

    private fun response(vararg places: String) = """{"places":[${places.joinToString(",")}]}"""

    // --- request ------------------------------------------------------------

    @Test
    fun `a search carries the input and its billing session, and no bias by default`() {
        val body = GooglePlaces.autocompleteBody("Camping Delta", near = null, sessionToken = "tok")
        assertEquals("""{"input":"Camping Delta","sessionToken":"tok"}""", body)
        assertFalse(body.contains("locationBias"))
    }

    @Test
    fun `the map centre becomes a circular bias`() {
        assertEquals(
            """{"input":"Camping","sessionToken":"tok",""" +
                """"locationBias":{"circle":{"center":{"latitude":46.95,"longitude":7.45},""" +
                """"radius":50000.0}}}""",
            GooglePlaces.autocompleteBody("Camping", LatLng(46.95, 7.45), sessionToken = "tok"),
        )
    }

    @Test
    fun `a query with quotes or newlines cannot break the request body`() {
        assertEquals(
            """{"input":"say \"hi\"\nnow\\","sessionToken":"t"}""",
            GooglePlaces.autocompleteBody("say \"hi\"\nnow\\", near = null, sessionToken = "t"),
        )
        assertEquals("\"a\\tb\"", "a\tb".jsonString())
        assertEquals("\"a\\rb\"", "a\rb".jsonString())
        assertEquals("\"\\u0001\"", "\u0001".jsonString())
    }

    @Test
    fun `a place id is escaped into the details path, with the session when there is one`() {
        assertEquals(
            "https://places.googleapis.com/v1/places/ChIJ%2Fa%20b",
            GooglePlaces.detailsUrl("ChIJ/a b"),
        )
        assertEquals(
            "https://places.googleapis.com/v1/places/ChIJ1?sessionToken=a%20b",
            GooglePlaces.detailsUrl("ChIJ1", "a b"),
        )
        assertTrue(GooglePlaces.DETAILS_FIELD_MASK.contains("location"))
    }

    // --- autocomplete response ----------------------------------------------

    private fun prediction(id: String = "ChIJ1", main: String? = "Grimsel Pass", secondary: String? = "Obergoms, Switzerland"): String {
        val format = buildList {
            main?.let { add(""""mainText":{"text":"$it"}""") }
            secondary?.let { add(""""secondaryText":{"text":"$it"}""") }
        }
        return """{"placePrediction":{"placeId":"$id","text":{"text":"$main, $secondary"},""" +
            """"structuredFormat":{${format.joinToString(",")}}}}"""
    }

    private fun suggestions(vararg items: String) = """{"suggestions":[${items.joinToString(",")}]}"""

    @Test
    fun `predictions come back named but not yet located`() {
        val parsed = GooglePlaces.parseAutocomplete(suggestions(prediction())).single()
        assertEquals("Grimsel Pass", parsed.name)
        assertEquals("Obergoms, Switzerland", parsed.label)
        assertEquals("ChIJ1", parsed.id)
        // The coordinate arrives only when the hit is chosen.
        assertNull(parsed.location)
    }

    @Test
    fun `one query becomes several things to choose from`() {
        val parsed = GooglePlaces.parseAutocomplete(
            suggestions(
                prediction("a", "Grimsel Pass"),
                prediction("b", "Grimselsee"),
                prediction("c", "Grimsel Hospiz"),
            ),
        )
        assertEquals(listOf("Grimsel Pass", "Grimselsee", "Grimsel Hospiz"), parsed.map { it.name })
    }

    @Test
    fun `a prediction with no structure falls back to its full text`() {
        assertEquals(
            "Grimsel Pass, Obergoms",
            GooglePlaces.parseAutocomplete(
                suggestions("""{"placePrediction":{"placeId":"x","text":{"text":"Grimsel Pass, Obergoms"}}}"""),
            ).single().name,
        )
    }

    @Test
    fun `query predictions and idless or nameless entries are skipped`() {
        assertTrue(
            GooglePlaces.parseAutocomplete(
                suggestions(
                    """{"queryPrediction":{"text":{"text":"pizza near me"}}}""",
                    """{"placePrediction":{"structuredFormat":{"mainText":{"text":"No id"}}}}""",
                    """{"placePrediction":{"placeId":"y"}}""",
                    "42",
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `duplicate predictions collapse and at most eight are shown`() {
        val many = (1..12).map { prediction("id$it", "Place $it") }
        assertEquals(8, GooglePlaces.parseAutocomplete(suggestions(*many.toTypedArray())).size)
        assertEquals(1, GooglePlaces.parseAutocomplete(suggestions(prediction(), prediction("other"))).size)
    }

    @Test
    fun `an empty list, an error body and junk all yield no predictions`() {
        assertTrue(GooglePlaces.parseAutocomplete("""{"suggestions":[]}""").isEmpty())
        assertTrue(GooglePlaces.parseAutocomplete("{}").isEmpty())
        assertTrue(
            GooglePlaces.parseAutocomplete("""{"error":{"code":403,"status":"PERMISSION_DENIED"}}""").isEmpty(),
        )
        assertTrue(GooglePlaces.parseAutocomplete("""{"suggestions":{"a":1}}""").isEmpty())
        assertTrue(GooglePlaces.parseAutocomplete("<html>404</html>").isEmpty())
        assertTrue(GooglePlaces.parseAutocomplete("[]").isEmpty())
        assertTrue(GooglePlaces.parseAutocomplete("").isEmpty())
    }

    // --- details response ---------------------------------------------------

    @Test
    fun `place details give back one place`() {
        val detail = GooglePlaces.parseDetails(place())!!
        assertEquals("ChIJ1", detail.id)
        assertEquals(LatLng(46.1712, 8.7936), detail.location)
    }

    @Test
    fun `details name themselves from the address when they have no display name`() {
        val detail = GooglePlaces.parseDetails(place(name = null))!!
        assertEquals("Via Respini 7, 6600 Locarno, Switzerland", detail.name)
        assertEquals("", detail.label)
    }

    @Test
    fun `details with neither a name nor an address are unusable`() {
        assertNull(GooglePlaces.parseDetails(place(name = null, address = null)))
    }

    @Test
    fun `unusable details are null rather than a throw`() {
        assertNull(GooglePlaces.parseDetails(place(location = null)))
        assertNull(GooglePlaces.parseDetails("not json"))
        assertNull(GooglePlaces.parseDetails("[]"))
    }

    @Test
    fun `a place with no id still parses, it just cannot be refreshed`() {
        assertEquals("", GooglePlaces.parseDetails("""{"displayName":{"text":"X"},"location":{"latitude":1.0,"longitude":2.0}}""")!!.id)
    }
}
