package ru.stepenko.rootme.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import ru.stepenko.rootme.exception.InvalidInput

/** Applies field presence and explicit null before binding the complete request. */
inline fun <reified T : Any> ObjectMapper.applyPatch(current: T, patch: JsonNode): T {
    if (!patch.isObject) throw InvalidInput("body", "Expected a JSON object.")
    val merged = valueToTree<ObjectNode>(current)
    patch.fields().forEach { (field, value) ->
        if (!merged.has(field)) throw InvalidInput(field, "This field cannot be updated.")
        merged.set<JsonNode>(field, value)
    }
    return treeToValue(merged, T::class.java)
}
