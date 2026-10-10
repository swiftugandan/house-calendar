package dev.housecalendar.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.housecalendar.contracts.IconKey
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's single icon set (DESIGN.md "Iconography"): 2 dp rounded strokes on a 24-unit grid, the same
 * drawing style as House Points. Bundled as vectors; nothing loads from the network. Tinted at the call site.
 */
object HcIcons {
    private fun stroke(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    val Plus by lazy { stroke("plus", "M12 5v14M5 12h14") }
    val Close by lazy { stroke("close", "M6 6l12 12M18 6L6 18") }
    val Back by lazy { stroke("back", "M15 18l-6-6 6-6") }
    val Forward by lazy { stroke("forward", "M9 6l6 6-6 6") }
    val Repeat by lazy { stroke("repeat", "M17 2l3 3-3 3M4 11V9a4 4 0 0 1 4-4h12M7 22l-3-3 3-3M20 13v2a4 4 0 0 1-4 4H4") }
    val Time by lazy { stroke("time", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2") }
    val Place by lazy { stroke("place", "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z") }
    val People by lazy { stroke("people", "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1M16 3.5a4 4 0 0 1 0 7.5M22 21v-1a6 6 0 0 0-4-5.6") }
    val Person by lazy { stroke("person", "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21v-1a7 7 0 0 1 7-7h2a7 7 0 0 1 7 7v1") }
    val Taking by lazy { stroke("taking", "M3 17v-5l2-5h14l2 5v5zM3 12h18M7 17v2M17 17v2M7 14.5h1M16 14.5h1") }
    val Bell by lazy { stroke("bell", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 21h4") }
    val Notes by lazy { stroke("notes", "M5 3h10l4 4v14H5zM15 3v4h4M8 12h8M8 16h6") }
    val Settings by lazy { stroke("settings", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6") }
    val Sync by lazy { stroke("sync", "M21 12a9 9 0 0 1-15.5 6.2L3 16M3 12a9 9 0 0 1 15.5-6.2L21 8M21 3v5h-5M3 21v-5h5") }
    val Edit by lazy { stroke("edit", "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3") }
    val Delete by lazy { stroke("delete", "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14") }
    val Check by lazy { stroke("check", "M5 12l5 5 9-10") }
    val Agenda by lazy { stroke("agenda", "M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01") }
    val Week by lazy { stroke("week", "M4 4h16v16H4zM4 9h16M4 14h16M9 4v16") }
    val Month by lazy { stroke("month", "M4 4h16v16H4zM4 9.3h16M4 14.6h16M9.3 4v16M14.6 4v16") }
    val Board by lazy { stroke("board", "M5 4h14v16H5zM9 8h6M9 12h6M9 16h4") }
    val Qr by lazy { stroke("qr", "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h3v3h-3zM18 18h3v3h-3z") }
    val Phone by lazy { stroke("phone", "M7 2h10v20H7zM11 18h2") }
    val Display by lazy { stroke("display", "M3 5h18v13H3zM9 21h6M12 18v3") }
    val Lock by lazy { stroke("lock", "M6 11h12v9H6zM8 11V8a4 4 0 0 1 8 0v3") }
    val Share by lazy { stroke("share", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M12 3v12M8 7l4-4 4 4") }
    val Import by lazy { stroke("import", "M12 3v12M8 11l4 4 4-4M4 17v3h16v-3") }
    val Undo by lazy { stroke("undo", "M9 14L4 9l5-5M4 9h11a5 5 0 0 1 0 10h-3") }
    val Clash by lazy { stroke("clash", "M9 16a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM15 18a5 5 0 1 0 0-10 5 5 0 0 0 0 10z") }
    val Holiday by lazy { stroke("holiday", "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4") }
    val Night by lazy { stroke("night", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }

    /** Event pictures, keyed by the [IconKey] stored in ops. The order is DESIGN.md's picker order. */
    val catalogue: List<Pair<IconKey, String>> = listOf(
        "swim" to "M2 17c2 0 2-1.5 4-1.5s2 1.5 4 1.5 2-1.5 4-1.5 2 1.5 4 1.5 2-1.5 4-1.5M2 21c2 0 2-1.5 4-1.5s2 1.5 4 1.5 2-1.5 4-1.5 2 1.5 4 1.5 2-1.5 4-1.5M17 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM5 13l5-4 3 3 3-1.5",
        "football" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 8l3.5 2.5-1.3 4H9.8l-1.3-4zM12 3v5M15.5 10.5L20 9M14.2 14.5l2.6 4M9.8 14.5l-2.6 4M8.5 10.5L4 9",
        "run" to "M14 5a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM6 21l3.5-5 3 2v-6l-3-2-3 2M12.5 10l3 3H19M10 16l4 5",
        "bike" to "M5.5 18a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM18.5 18a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM5.5 14.5l4-6.5h6l3 6.5M9.5 8l3 6.5h-7M14 5h3l1.5 3",
        "dance" to "M12 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM12 8v7l-3 6M12 15l3 6M7 6l5 3 5-5",
        "gym" to "M6 7v10M3 9v6M18 7v10M21 9v6M6 12h12",
        "music" to "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
        "art" to "M12 21a9 9 0 1 1 9-9c0 2.5-2 3.5-4 3.5h-2a2 2 0 0 0-1.5 3.3c.6.7.3 2.2-1.5 2.2zM7.5 11h.01M10 7h.01M15 7h.01M17 11h.01",
        "book" to "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 21V5M8 7h7",
        "school" to "M3 21h18M5 21V10l7-5 7 5v11M10 21v-5h4v5M12 5V2h3",
        "graduation" to "M2 9l10-5 10 5-10 5zM6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5M22 9v6",
        "doctor" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 8v8M8 12h8",
        "tooth" to "M7 3C4.5 3 3 5 3 7.5c0 3 1.5 4.5 2 7.5s1 6 2.5 6 2-4 4.5-4 3 4 4.5 4 2-3 2.5-6 2-4.5 2-7.5C21 5 19.5 3 17 3c-2 0-3 1-5 1S9 3 7 3z",
        "pill" to "M10.5 20.5l10-10a5 5 0 0 0-7-7l-10 10a5 5 0 0 0 7 7zM8.5 8.5l7 7",
        "cake" to "M4 21h16M5 21v-8h14v8M5 16c2 1.5 4.5 1.5 7 0s5-1.5 7 0M12 13V9M12 6.5A1.5 1.5 0 0 1 10.5 5c0-1 1.5-3 1.5-3s1.5 2 1.5 3A1.5 1.5 0 0 1 12 6.5z",
        "party" to "M12 15c3.3 0 6-3 6-6.5S15.3 2 12 2 6 5 6 8.5 8.7 15 12 15zM11 17h2l-1-2zM12 17c0 2 2 2.5 1 5",
        "gift" to "M3 8h18v4H3zM5 12v9h14v-9M12 8v13M12 8C10.5 4 6 4 6 6.5S10 8 12 8zM12 8c1.5-4 6-4 6-1.5S14 8 12 8z",
        "food" to "M4 3v7a2 2 0 0 0 4 0V3M6 12v9M18 3c-2.5 1.5-3 5-3 8h3v10",
        "cook" to "M2 11h14v1a6 6 0 0 1-6 6H8a6 6 0 0 1-6-6zM16 12h6M7 7c0-1.5 1-1.5 1-3M11 7c0-1.5 1-1.5 1-3",
        "shop" to "M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2",
        "car" to "M3 17v-5l2-5h14l2 5v5zM3 12h18M7 17v2M17 17v2M7 14.5h1M16 14.5h1",
        "bus" to "M5 4h14a1 1 0 0 1 1 1v12H4V5a1 1 0 0 1 1-1zM4 11h16M6.5 14.5h1M16.5 14.5h1M7 17v3M17 17v3",
        "train" to "M6 3h12a2 2 0 0 1 2 2v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V5a2 2 0 0 1 2-2zM4 11h16M12 3v8M8 21l2-3M16 21l-2-3M8 14.5h.01M16 14.5h.01",
        "plane" to "M2 13l20-8-6 15-4-6zM12 14l4-6",
        "holiday" to "M12 12v9M3 12a9 9 0 0 1 18 0zM8 21h8",
        "tent" to "M12 3L2 21h20zM8.5 21L12 14l3.5 7",
        "home" to "M3 11l9-7 9 7M5 9.5V21h14V9.5M10 21v-6h4v6",
        "work" to "M3 7h18v13H3zM8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18",
        "phone" to "M7 2h10v20H7zM11 18h2",
        "bin" to "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14M10 10v6M14 10v6",
        "recycle" to "M7 19H4l2.5-4.5M7 19l-1.5-2.5M17 19h3l-2.5-4.5M17 19l1.5-2.5M9.5 6L12 2l2.5 4M12 2l-1 3M9 19h6",
        "wash" to "M4 3h16v18H4zM4 7h16M12 18a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
        "pet" to "M12 20c-3 0-6-1.5-6-4s3-5 6-5 6 2.5 6 5-3 4-6 4zM5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM9 7a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM15 7a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM19 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z",
        "vet" to "M8 21a3 3 0 1 1 0-6c1.5 0 2 1 4 1s2.5-1 4-1a3 3 0 1 1 0 6zM6 11a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM18 11a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM12 9V3M9 6h6",
        "haircut" to "M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM8.5 7.5L20 18M8.5 16.5L20 6",
        "church" to "M12 2v6M9.5 4.5h5M5 21V11l7-3 7 3v10zM10 21v-5h4v5",
        "film" to "M3 5h18v14H3zM7 5v14M17 5v14M3 9.5h4M3 14.5h4M17 9.5h4M17 14.5h4",
        "game" to "M6 8h12a4 4 0 0 1 4 4v2a3 3 0 0 1-5.4 1.8L15 14H9l-1.6 1.8A3 3 0 0 1 2 14v-2a4 4 0 0 1 4-4zM7 11v3M5.5 12.5h3M16 11.5h.01M18 13h.01",
        "star" to "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z",
        "heart" to "M12 20s-8-4.8-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6.2-8 11-8 11z",
    ).map { (key, path) -> IconKey(key) to path }

    private val paths: Map<IconKey, String> = catalogue.toMap()
    private val vectors = ConcurrentHashMap<IconKey, ImageVector>()

    /** The picture for [key], or null for no icon or a key a newer version added. */
    fun of(key: IconKey): ImageVector? = paths[key]?.let { path -> vectors.getOrPut(key) { stroke(key.key, path) } }
}

/** SPEC FR-14: an icon from the title, through an English keyword table (in precedence order). */
object IconSuggester {
    val keywords: Map<String, IconKey> = linkedMapOf(
        "birthday" to "cake", "bday" to "cake", "cake" to "cake",
        "dentist" to "tooth", "orthodontist" to "tooth", "dental" to "tooth",
        "vet" to "vet",
        "swim" to "swim", "swimming" to "swim", "pool" to "swim", "gala" to "swim",
        "football" to "football", "soccer" to "football", "rugby" to "football", "match" to "football",
        "run" to "run", "running" to "run", "parkrun" to "run", "athletics" to "run",
        "bike" to "bike", "cycling" to "bike", "bikeability" to "bike",
        "dance" to "dance", "dancing" to "dance", "ballet" to "dance", "gymnastics" to "dance",
        "gym" to "gym", "fitness" to "gym", "yoga" to "gym", "pilates" to "gym", "karate" to "gym",
        "music" to "music", "piano" to "music", "guitar" to "music", "violin" to "music", "choir" to "music", "drums" to "music",
        "art" to "art", "painting" to "art", "drawing" to "art", "craft" to "art",
        "reading" to "book", "library" to "book", "homework" to "book", "book" to "book",
        "graduation" to "graduation", "exam" to "graduation", "exams" to "graduation",
        "school" to "school", "inset" to "school", "assembly" to "school", "nursery" to "school",
        "doctor" to "doctor", "gp" to "doctor", "hospital" to "doctor", "clinic" to "doctor", "physio" to "doctor", "optician" to "doctor",
        "medicine" to "pill", "pharmacy" to "pill", "prescription" to "pill", "vaccine" to "pill", "jab" to "pill", "jabs" to "pill",
        "party" to "party", "disco" to "party", "sleepover" to "party",
        "present" to "gift", "presents" to "gift", "gift" to "gift", "christmas" to "gift",
        "dinner" to "food", "lunch" to "food", "breakfast" to "food", "restaurant" to "food", "takeaway" to "food",
        "cooking" to "cook", "baking" to "cook", "bake" to "cook",
        "shopping" to "shop", "supermarket" to "shop", "groceries" to "shop",
        "mot" to "car", "garage" to "car", "car" to "car",
        "bus" to "bus", "coach" to "bus",
        "train" to "train",
        "flight" to "plane", "plane" to "plane", "airport" to "plane",
        "holiday" to "holiday", "holidays" to "holiday", "beach" to "holiday",
        "camping" to "tent", "camp" to "tent", "scouts" to "tent", "cubs" to "tent", "beavers" to "tent", "brownies" to "tent", "guides" to "tent",
        "plumber" to "home", "electrician" to "home", "cleaner" to "home", "home" to "home",
        "work" to "work", "meeting" to "work", "office" to "work", "shift" to "work",
        "call" to "phone", "facetime" to "phone", "zoom" to "phone",
        "recycling" to "recycle",
        "bin" to "bin", "bins" to "bin", "rubbish" to "bin",
        "laundry" to "wash", "washing" to "wash",
        "dog" to "pet", "cat" to "pet", "pet" to "pet",
        "haircut" to "haircut", "hairdresser" to "haircut", "barber" to "haircut",
        "church" to "church", "mosque" to "church", "temple" to "church", "synagogue" to "church",
        "film" to "film", "cinema" to "film", "movie" to "film",
        "game" to "game", "gaming" to "game",
        "anniversary" to "heart", "valentine" to "heart",
    ).mapValues { IconKey(it.value) }

    fun suggest(title: String): IconKey {
        val words = title.lowercase(java.util.Locale.ROOT).split(NON_LETTERS).filter { it.isNotEmpty() }.toSet()
        return keywords.entries.firstOrNull { it.key in words }?.value ?: IconKey.NONE
    }

    private val NON_LETTERS = Regex("[^a-z]+")
}
