package id.walt.oid4vc.data

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.descriptors.buildClassSerialDescriptor

// TODO: reconsider nested claims handling, which seems to be mis-specified (mixing claim properties and nested properties)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = ClaimDescriptorSerializer::class)
data class ClaimDescriptor(
    val mandatory: Boolean? = null,
    @SerialName("value_type") val valueType: String? = null,
    @Serializable(DisplayPropertiesListSerializer::class) val display: List<DisplayProperties>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    val nestedClaims: Map<String, ClaimDescriptor> = customParameters!!.filterValues { it is JsonObject }
        .mapValues { fromJSON(it.value.jsonObject) }

    override fun toJSON() = Json.encodeToJsonElement(ClaimDescriptorSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<ClaimDescriptor>() {
        override fun fromJSON(jsonObject: JsonObject): ClaimDescriptor =
            Json.decodeFromJsonElement(ClaimDescriptorSerializer, jsonObject)
    }
}

internal object ClaimDescriptorSerializer :
    JsonDataObjectSerializer<ClaimDescriptor>(ClaimDescriptor.generatedSerializer())

internal object ClaimDescriptorMapSerializer : KSerializer<Map<String, ClaimDescriptor>> {
    private val internalSerializer = MapSerializer(String.serializer(), ClaimDescriptorSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): Map<String, ClaimDescriptor> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Map<String, ClaimDescriptor>) =
        internalSerializer.serialize(encoder, value)
}

internal object ClaimDescriptorNamespacedMapSerializer : KSerializer<Map<String, Map<String, ClaimDescriptor>>> {
    private val internalSerializer =
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), ClaimDescriptorSerializer))
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): Map<String, Map<String, ClaimDescriptor>> =
        internalSerializer.deserialize(decoder)

    override fun serialize(encoder: Encoder, value: Map<String, Map<String, ClaimDescriptor>>) =
        internalSerializer.serialize(encoder, value)
}

// เพิ่มท้ายไฟล์ ClaimDescriptor.kt

/**
 * OID4VCI 1.0 Final — claim description ใน array format
 */
@Serializable
data class ClaimDescriptionOID4VCI(
    val path: List<String>,
    val mandatory: Boolean? = null,
    val display: List<DisplayProperties>? = null,
)

/**
 * Flexible serializer รองรับทั้ง 2 format:
 * - format เก่า (object): {"": {"FirstName": {"mandatory": true}}}
 * - format ใหม่ (array):  [{"path": ["FirstName"], "mandatory": true}]
 */
internal object ClaimDescriptorFlexibleSerializer :
    KSerializer<Map<String, Map<String, ClaimDescriptor>>?> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("FlexibleClaims")

    override fun deserialize(decoder: Decoder): Map<String, Map<String, ClaimDescriptor>>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            // ✅ format ใหม่ตาม OID4VCI 1.0: array
            is JsonArray -> {
                val grouped = mutableMapOf<String, MutableMap<String, ClaimDescriptor>>()
                element.forEach { item ->
                    val obj = item as? JsonObject ?: return@forEach
                    val path = obj["path"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: return@forEach
                    val namespace = if (path.size > 1) path[0] else ""
                    val fieldName = path.last()
                    val mandatory = obj["mandatory"]?.jsonPrimitive?.booleanOrNull
                    val display = obj["display"]?.let {
                        Json.decodeFromJsonElement(DisplayPropertiesListSerializer, it)
                    }
                    grouped.getOrPut(namespace) { mutableMapOf() }[fieldName] =
                        ClaimDescriptor(mandatory = mandatory, display = display)
                }
                grouped
            }
            // ✅ format เก่า: object/namespace map
            is JsonObject -> {
				val result = mutableMapOf<String, MutableMap<String, ClaimDescriptor>>()
				element.forEach { (namespace, namespaceValue) ->
					val namespaceObj = namespaceValue as? JsonObject ?: return@forEach
					val claimsMap = mutableMapOf<String, ClaimDescriptor>()
					namespaceObj.forEach { (claimName, claimValue) ->
						val claimObj = claimValue as? JsonObject ?: return@forEach
						claimsMap[claimName] = ClaimDescriptor.fromJSON(claimObj)
					}
					result[namespace] = claimsMap
				}
				result
			}
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, Map<String, ClaimDescriptor>>?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            ClaimDescriptorNamespacedMapSerializer.serialize(
                encoder,
                value
            )
        }
    }
}
