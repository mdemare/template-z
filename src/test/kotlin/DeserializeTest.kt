import kotlinx.serialization.json.Json
import nl.mdemare.Root

fun main() {
    val json =
        "{\"players\":[{\"roleName\":\"medic\",\"playerHands\":[{\"color\":\"red\"}]},{\"roleName\":\"researcher\",\"playerHands\":[{\"color\":\"blue\"},{\"color\":\"yellow\"}]}],\"panel\":{\"speed\":\"112\"}}"
    val root = Json.decodeFromString<Root>(json)
}
