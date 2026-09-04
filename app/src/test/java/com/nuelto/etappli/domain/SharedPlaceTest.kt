package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real payloads: what Google Maps, WhatsApp and the geo: scheme actually send. */
class SharedPlaceTest {

    private fun parse(text: String?, subject: String = "") = SharedPlace.parse(text, subject)

    private fun at(text: String): LatLng = requireNotNull(parse(text)?.location)

    @Test
    fun `the place coordinate beats the map centre`() {
        val place = parse(
            "https://www.google.com/maps/place/Grimselpass/@46.5,8.3,15z/" +
                "data=!4m6!3m5!1s0x0:0x0!8m2!3d46.5601!4d8.3320",
        )!!
        assertEquals("Grimselpass", place.name)
        assertEquals(LatLng(46.5601, 8.332), place.location)
        assertFalse(place.approximate)
    }

    @Test
    fun `the last data pair is the place`() {
        assertEquals(
            LatLng(48.856614, 2.3522219),
            at("https://www.google.com/maps/place/X/data=!3d37.0625!4d-95.677!4m2!3d48.856614!4d2.3522219"),
        )
    }

    @Test
    fun `a map centre on its own is only approximate`() {
        val place = parse("https://www.google.com/maps/place/Anand+Tea+Stall/@26.4989241,80.1953814,12z")!!
        assertEquals(LatLng(26.4989241, 80.1953814), place.location)
        assertTrue(place.approximate)
        assertEquals("Anand Tea Stall", place.name)
        // The camera suffix varies with what Maps was showing.
        assertEquals(LatLng(46.5, 8.3), at("https://www.google.com/maps/@46.5,8.3,11.42z"))
        assertEquals(LatLng(46.5, 8.3), at("https://www.google.com/maps/@46.5,8.3,247m/data=!3m1!1e3"))
    }

    @Test
    fun `our own share link round-trips`() {
        val url = MapsUri.share("Grimsel Pass", LatLng(46.5606, 8.3376), "ChIJCyinolJ-hUcRIQiP1AQWa4s")!!
        val place = parse(url)!!
        assertEquals(LatLng(46.5606, 8.3376), place.location)
        assertEquals("ChIJCyinolJ-hUcRIQiP1AQWa4s", place.placeId)
    }

    @Test
    fun `a dropped pin carries its coordinate in the path`() {
        assertEquals(LatLng(5.811698, -55.118891), at("https://www.google.com/maps/search/5.811698,+-55.118891"))
    }

    @Test
    fun `a query that is not a coordinate is a name`() {
        val place = parse("https://www.google.com/maps/search/?api=1&query=Camping%20Delta%20Locarno")!!
        assertEquals("Camping Delta Locarno", place.name)
        assertNull(place.location)
    }

    @Test
    fun `the legacy loc form keeps its label`() {
        val place = parse("https://maps.google.com/maps?q=loc:46.8182,8.2275%20(Rütli)")!!
        assertEquals("Rütli", place.name)
        assertEquals(LatLng(46.8182, 8.2275), place.location)
    }

    @Test
    fun `geo carries a coordinate, a label, or an address`() {
        assertEquals(LatLng(46.5601, 8.332), at("geo:46.5601,8.332"))
        assertEquals(LatLng(46.5601, 8.332), at("geo:46.5601,8.332?z=15"))
        assertEquals(LatLng(46.5, 8.3), at("geo:46.5,8.3;u=35"))

        val labelled = parse("geo:0,0?q=46.5601,8.332(Grimselpass)")!!
        assertEquals("Grimselpass", labelled.name)
        assertEquals(LatLng(46.5601, 8.332), labelled.location)

        val address = parse("geo:0,0?q=Bahnhofplatz+3,+7000+Chur")!!
        assertEquals("Bahnhofplatz 3, 7000 Chur", address.name)
        assertNull(address.location)
    }

    @Test
    fun `the geo zero sentinel is nowhere`() {
        assertNull(parse("geo:0,0"))
        assertNull(parse("geo:0,0?z=15"))
    }

    @Test
    fun `only the four short-link hosts are followed`() {
        listOf("maps.app.goo.gl/aBcD1234", "goo.gl/maps/x1", "app.goo.gl/x1", "g.co/kgs/x1").forEach { host ->
            assertEquals("https://$host", parse("https://$host")?.link)
            assertTrue(SharedPlace.isShortLink("https://$host"))
        }
        assertNull(parse("https://maps.app.goo.gl.evil.com/x"))
        assertNull(parse("https://evil.com/maps.app.goo.gl/x"))
        // Userinfo is not a host: this one belongs to evil.com.
        assertNull(parse("https://maps.app.goo.gl@evil.com/x"))
        assertFalse(SharedPlace.isShortLink("https://maps.google.com/maps?q=1,2"))
    }

    @Test
    fun `a short link is upgraded to https`() {
        assertEquals("https://goo.gl/maps/x1", parse("http://goo.gl/maps/x1")?.link)
    }

    @Test
    fun `the dynamic-link form needs no network`() {
        val place = parse(
            "https://maps.app.goo.gl/?apn=com.google.android.apps.maps&link=" +
                "https%3A%2F%2Fwww.google.com%2Fmaps%2Fplace%2FThun%2Fdata%3D!3d46.758!4d7.628",
        )!!
        assertEquals("Thun", place.name)
        assertEquals(LatLng(46.758, 7.628), place.location)
        assertEquals("", place.link)
        // A dynamic link pointing at nothing usable is still worth a redirect.
        assertEquals(
            "https://maps.app.goo.gl/?link=https%3A%2F%2Fexample.com%2Fx",
            parse("https://maps.app.goo.gl/?link=https%3A%2F%2Fexample.com%2Fx")?.link,
        )
    }

    @Test
    fun `the share sheet's own name is kept`() {
        val place = parse("Camping Grimselblick\nGrimselstrasse\nhttps://maps.app.goo.gl/aBcD1234")!!
        assertEquals("Camping Grimselblick", place.name)
        assertEquals("https://maps.app.goo.gl/aBcD1234", place.link)
        assertNull(place.location)
    }

    @Test
    fun `the subject names an otherwise nameless share`() {
        assertEquals(
            "Camping Kirnbergsee",
            parse("https://maps.app.goo.gl/x1", subject = "Camping Kirnbergsee")?.name,
        )
    }

    @Test
    fun `only a real place id is kept`() {
        val kept = "https://www.google.com/maps/search/?api=1&query=46.5,8.3&query_place_id=ChIJCyinolJ-hUcR"
        assertEquals("ChIJCyinolJ-hUcR", parse(kept)?.placeId)
        // Too short to be one, and the hex ftid and cid forms are nobody's place id.
        assertEquals("", parse("$kept-!".dropLast(2).replace("ChIJCyinolJ-hUcR", "ChIJ1"))?.placeId)
        assertEquals(
            "",
            parse("https://www.google.com/maps/place/X/data=!1s0x47a8c:0x26bb!8m2!3d46.5!4d8.3")?.placeId,
        )
        assertEquals("", parse("https://maps.google.com/?q=46.5,8.3&cid=2791100708733537379")?.placeId)
    }

    @Test
    fun `country domains are Google, lookalikes are not`() {
        listOf("www.google.co.uk", "maps.google.de", "google.ch").forEach { host ->
            assertEquals(LatLng(46.5, 8.3), at("https://$host/maps/place/X/data=!3d46.5!4d8.3"))
        }
        assertNull(parse("https://google.evil.com/maps/place/X/data=!3d46.5!4d8.3"))
        assertNull(parse("https://notgoogle.com/maps/place/X/data=!3d46.5!4d8.3"))
    }

    @Test
    fun `junk around the link is stripped`() {
        assertEquals(
            LatLng(46.5, 8.3),
            at("Look: https://www.google.com/maps/place/X/data=!3d46.5!4d8.3%3C%2Fa%3E"),
        )
        assertEquals(LatLng(46.5, 8.3), at("https://www.google.com/maps/place/X/data=!3d46.5!4d8.3."))
        // A right-to-left mark inside the payload is not part of the name.
        assertEquals("Grimsel", parse("‭Grimsel‬\nhttps://maps.app.goo.gl/x1")?.name)
    }

    @Test
    fun `coordinates off the globe are refused`() {
        assertNull(parse("https://www.google.com/maps/search/?api=1&query=91.0,8.3"))
        assertNull(parse("https://www.google.com/maps/search/?api=1&query=46.5,181.0"))
        assertNull(parse("https://www.google.com/maps/search/?api=1&query=0,0"))
        assertNull(parse("46.5"))
        // A named place whose coordinate is nonsense still hands over the name.
        val named = parse("https://www.google.com/maps/place/X/data=!3d91.0!4d8.3")!!
        assertEquals("X", named.name)
        assertNull(named.location)
    }

    @Test
    fun `an address is a name, never a pin`() {
        // "Flat 2, 14 Baker Street" is a house number and a street, not 2°N 14°E.
        val flat = parse("geo:0,0?q=Flat 2, 14 Baker Street, London")!!
        assertEquals("Flat 2, 14 Baker Street, London", flat.name)
        assertNull(flat.location)

        val labelled = parse("geo:0,0?q=Unit 3, 45 George St(Camping George)")!!
        assertEquals("Camping George", labelled.name)
        assertNull(labelled.location)

        val query = parse("https://www.google.com/maps/search/?api=1&query=Apt%205%2C%20120%20Main%20St")!!
        assertEquals("Apt 5, 120 Main St", query.name)
        assertNull(query.location)

        // Free text needs decimals before it counts as a coordinate, and text with
        // neither a link nor a coordinate is not a place at all.
        assertNull(parse("Camping Sussex\nFlat 2, 14 Sea Lane, Brighton"))
    }

    @Test
    fun `the map centre in a directions link is only approximate`() {
        // "ll" is where the map was looking; the destination is what was meant.
        val place = parse("https://maps.google.com/maps?daddr=Camping+Delta&ll=46.9,7.4")!!
        assertEquals("Camping Delta", place.name)
        assertEquals(LatLng(46.9, 7.4), place.location)
        assertTrue(place.approximate)
    }

    @Test
    fun `a coordinate beside a link fills the link's blank`() {
        val place = parse("Camping Grimselblick 46.5601, 8.332\nhttps://maps.app.goo.gl/x1")!!
        assertEquals(LatLng(46.5601, 8.332), place.location)
        // Still worth following: the redirect knows the place, not just the pin.
        assertEquals("https://maps.app.goo.gl/x1", place.link)
    }

    @Test
    fun `the poles and the date line are still on the globe`() {
        assertEquals(LatLng(90.0, 180.0), at("https://www.google.com/maps/place/X/data=!3d90.0!4d180.0"))
        assertEquals(LatLng(-90.0, -180.0), at("https://www.google.com/maps/place/X/data=!3d-90.0!4d-180.0"))
    }

    @Test
    fun `a geo query outranks the coordinate in its path`() {
        // RFC 5870 puts the map centre in the path; "q" is the place actually meant.
        assertEquals(LatLng(46.5601, 8.332), at("geo:47.0,7.0?q=46.5601,8.332"))
    }

    @Test
    fun `a place segment that is really a coordinate is no name`() {
        val place = parse("https://www.google.com/maps/place/46.5,8.3")!!
        assertEquals("", place.name)
        assertEquals(LatLng(46.5, 8.3), place.location)
    }

    @Test
    fun `a decoded name survives`() {
        assertEquals(
            "Poznań Old Town, Poland",
            parse("https://www.google.com/maps/place/Pozna%C5%84+Old+Town,+Poland/@52.4,16.9,15z")?.name,
        )
        // A percent sign that decodes to nothing is left as it stands.
        assertEquals("100%zoom", parse("https://www.google.com/maps/place/100%zoom/@52.4,16.9,15z")?.name)
    }

    @Test
    fun `a list, a drawing or a live location is not a place`() {
        assertNull(parse("https://www.google.com/maps/placelists/list/abc?entry=tts"))
        assertNull(parse("https://www.google.com/maps/d/viewer?mid=abc"))
        assertNull(parse("https://www.google.com/maps/@46.5,8.3,15z/data=!4m5!7m4!1m2!1sx!2e2"))
    }

    @Test
    fun `bare coordinates in text are the last resort`() {
        assertEquals(LatLng(46.8182, 8.2275), at("Meet me at 46.8182, 8.2275 tomorrow"))
        // Digits inside an unusable link are not a coordinate.
        assertNull(parse("https://example.com/x/46.8182,8.2275"))
    }

    @Test
    fun `17 decimals survive`() {
        assertEquals(
            LatLng(46.5, 16.929066199999998),
            at("https://www.google.com/maps/place/X/data=!3d46.5!4d16.929066199999998"),
        )
    }

    @Test
    fun `empty and oversized payloads name nowhere`() {
        assertNull(parse(null))
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("hello"))
        assertNull(parse("x".repeat(SharedPlace.MAX_TEXT) + " 46.5,8.3"))
    }

    @Test
    fun `the Maps app's link names the place and pins nothing, so the name is looked up`() {
        val redirect = "https://www.google.com/maps/place/Zielhaus+am+Klausenpass/" +
            "data=!4m2!3m1!1s0x479a7a1b2c3d4e5f:0x6a7b8c9d0e1f2a3b?utm_source=mstt_1&entry=gps&g_ep=CAESBzI"
        val expanded = parse("https://maps.app.goo.gl/aBcD1234")!!.expandedWith(redirect)
        assertEquals("Zielhaus am Klausenpass", expanded.name)
        assertNull(expanded.location)
        assertEquals("", expanded.placeId)
        assertEquals("", expanded.link)
        assertTrue(expanded.needsLookup)
    }

    @Test
    fun `only a name with no spot, or an approximate one, needs looking up`() {
        assertTrue(SharedPlace("Zielhaus am Klausenpass").needsLookup)
        assertTrue(SharedPlace("Titisee", LatLng(47.89, 8.14), approximate = true).needsLookup)
        assertFalse(SharedPlace("Titisee", LatLng(47.89, 8.14)).needsLookup)
        // Not before the link has been followed; a place id fetches its own coordinate.
        assertFalse(SharedPlace("Camping X", link = "https://maps.app.goo.gl/x1").needsLookup)
        assertFalse(SharedPlace("Camping X", placeId = "ChIJCyinolJ-hUcR").needsLookup)
        assertFalse(SharedPlace(location = LatLng(46.5, 8.3), approximate = true).needsLookup)
        assertFalse(SharedPlace().needsLookup)
    }

    @Test
    fun `the one place found for the name becomes the pin, on the Places clock`() {
        val shared = SharedPlace("Titisee", LatLng(47.89, 8.14), approximate = true)
        val located = shared.locatedBy(PlaceSuggestion("Titisee", "Baden-Württemberg", LatLng(47.8918, 8.1454), "ChIJ1"))
        assertEquals("Titisee", located.name)
        assertEquals(LatLng(47.8918, 8.1454), located.location)
        assertEquals("ChIJ1", located.placeId)
        assertFalse(located.approximate)
        assertTrue(located.fromPlaces)
        assertFalse(located.needsLookup)
        // No hit, or one that has no coordinate, changes nothing.
        assertEquals(shared, shared.locatedBy(null))
        assertEquals(shared, shared.locatedBy(PlaceSuggestion("Titisee", "")))
        // A hit without an id keeps the one the share had.
        val byId = SharedPlace("X", placeId = "ChIJ2").locatedBy(PlaceSuggestion("X", "", LatLng(1.0, 2.0)))
        assertEquals("ChIJ2", byId.placeId)
    }

    @Test
    fun `expanding a link folds the redirect in and keeps the name`() {
        val short = parse("Camping Grimselblick\nhttps://maps.app.goo.gl/aBcD1234")!!
        val expanded = short.expandedWith(
            "https://www.google.com/maps/place/Grimselblick/data=!3d46.5601!4d8.332" +
                "?query_place_id=ChIJCyinolJ-hUcR",
        )
        assertEquals("Camping Grimselblick", expanded.name)
        assertEquals(LatLng(46.5601, 8.332), expanded.location)
        assertEquals("ChIJCyinolJ-hUcR", expanded.placeId)
        assertEquals("", expanded.link)
        assertFalse(expanded.approximate)
    }

    @Test
    fun `a redirect that names nowhere leaves the link to retry`() {
        val short = parse("https://maps.app.goo.gl/aBcD1234")!!
        assertEquals(short, short.expandedWith(null))
        assertEquals(short, short.expandedWith("https://evil.com/?q=46.5,8.3"))
        assertEquals(short, short.expandedWith("https://goo.gl/maps/other"))
    }

    @Test
    fun `an expanded map centre stays approximate, and a named link keeps its own name`() {
        val short = parse("https://maps.app.goo.gl/aBcD1234")!!
        val expanded = short.expandedWith("https://www.google.com/maps/place/Titisee/@47.8918,8.1454,15z")
        assertEquals("Titisee", expanded.name)
        assertTrue(expanded.approximate)
        // A redirect with only a name keeps whatever the share already knew.
        val named = SharedPlace(location = LatLng(46.5, 8.3), link = "https://goo.gl/maps/x")
        val byName = named.expandedWith("https://www.google.com/maps/search/?api=1&query=Camping%20Delta")
        assertEquals("Camping Delta", byName.name)
        assertEquals(LatLng(46.5, 8.3), byName.location)
        assertFalse(byName.approximate)
    }
}
