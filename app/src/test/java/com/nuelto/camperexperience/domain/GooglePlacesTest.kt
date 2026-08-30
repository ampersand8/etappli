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
    fun `a search asks for the fields it uses and no more`() {
        val body = GooglePlaces.searchBody("Camping Delta", near = null, language = "en")
        assertEquals(
            """{"textQuery":"Camping Delta","pageSize":5,"languageCode":"en"}""",
            body,
        )
        assertFalse(body.contains("locationBias"))
        assertEquals(
            "places.id,places.displayName,places.formattedAddress,places.location",
            GooglePlaces.FIELD_MASK,
        )
    }

    @Test
    fun `the map centre becomes a circular bias`() {
        assertEquals(
            """{"textQuery":"Camping","pageSize":5,"languageCode":"de",""" +
                """"locationBias":{"circle":{"center":{"latitude":46.95,"longitude":7.45},""" +
                """"radius":50000.0}}}""",
            GooglePlaces.searchBody("Camping", LatLng(46.95, 7.45), language = "de"),
        )
    }

    @Test
    fun `a query with quotes or newlines cannot break the request body`() {
        assertEquals(
            """{"textQuery":"say \"hi\"\nnow\\","pageSize":5,"languageCode":"en"}""",
            GooglePlaces.searchBody("say \"hi\"\nnow\\", near = null, language = "en"),
        )
        assertEquals("\"a\\tb\"", "a\tb".jsonString())
        assertEquals("\"a\\rb\"", "a\rb".jsonString())
        assertEquals("\"\\u0001\"", "\u0001".jsonString())
    }

    @Test
    fun `a place id is escaped into the details path`() {
        assertEquals(
            "https://places.googleapis.com/v1/places/ChIJ%2Fa%20b",
            GooglePlaces.detailsUrl("ChIJ/a b"),
        )
        assertTrue(GooglePlaces.DETAILS_FIELD_MASK.contains("location"))
    }

    // --- search response ----------------------------------------------------

    @Test
    fun `parses the display name, address and coordinate`() {
        assertEquals(
            PlaceSuggestion(
                name = "Camping Delta",
                label = "Via Respini 7, 6600 Locarno, Switzerland",
                location = LatLng(46.1712, 8.7936),
                id = "ChIJ1",
            ),
            GooglePlaces.parseSearch(response(place())).single(),
        )
    }

    @Test
    fun `a nameless place falls back to its address, and loses the duplicate label`() {
        val parsed = GooglePlaces.parseSearch(response(place(name = null))).single()
        assertEquals("Via Respini 7, 6600 Locarno, Switzerland", parsed.name)
        assertEquals("", parsed.label)
    }

    @Test
    fun `places without a name or a coordinate are dropped`() {
        assertTrue(
            GooglePlaces.parseSearch(
                response(
                    place(name = null, address = null),
                    place(location = null),
                    place(location = """"location":{"latitude":46.1}"""),
                    place(location = """"location":{"longitude":8.7}"""),
                    """{"id":"x","location":"46,8"}""",
                    "42",
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `a coordinate sent as a string is not trusted`() {
        assertTrue(
            GooglePlaces.parseSearch(
                response(place(location = """"location":{"latitude":"46.1","longitude":"8.7"}""")),
            ).isEmpty(),
        )
    }

    @Test
    fun `the same place twice collapses and at most five are shown`() {
        val many = (1..8).map { place(id = "id$it", name = "Camp $it") }
        assertEquals(5, GooglePlaces.parseSearch(response(*many.toTypedArray())).size)
        assertEquals(1, GooglePlaces.parseSearch(response(place(), place(id = "other"))).size)
    }

    @Test
    fun `an empty list, an error body and junk all yield nothing`() {
        assertTrue(GooglePlaces.parseSearch("""{"places":[]}""").isEmpty())
        assertTrue(GooglePlaces.parseSearch("{}").isEmpty())
        assertTrue(
            GooglePlaces.parseSearch(
                """{"error":{"code":403,"message":"API key not valid","status":"PERMISSION_DENIED"}}""",
            ).isEmpty(),
        )
        assertTrue(GooglePlaces.parseSearch("""{"places":{"id":"x"}}""").isEmpty())
        assertTrue(GooglePlaces.parseSearch("<html>404</html>").isEmpty())
        assertTrue(GooglePlaces.parseSearch("[]").isEmpty())
        assertTrue(GooglePlaces.parseSearch("").isEmpty())
    }

    // --- details response ---------------------------------------------------

    @Test
    fun `place details give back one place`() {
        val detail = GooglePlaces.parseDetails(place())!!
        assertEquals("ChIJ1", detail.id)
        assertEquals(LatLng(46.1712, 8.7936), detail.location)
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
