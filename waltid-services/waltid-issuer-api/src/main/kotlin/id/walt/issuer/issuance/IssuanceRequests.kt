package id.walt.issuer.issuance

import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.*
import id.walt.sdjwt.DecoyMode
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.toSDMap
import id.walt.w3c.vc.vcs.W3CVC
import io.ktor.server.plugins.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ─────────────────────────────────────────────────────────────────────────────
// OID4VCI 1.0 Final — Appendix B/C: Claims Description
// https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a single claim descriptor per OID4VCI 1.0 Final spec (Appendix B/C).
 *
 * Example JSON:
 * ```json
 * { "path": ["FirstName"], "mandatory": true, "sd": true }
 * ```
 *
 * @param path      Claims Path Pointer — list of keys to reach the claim
 *                  (e.g. ["credentialSubject", "firstName"] or just ["firstName"])
 * @param mandatory Whether the claim MUST be present in the credential
 * @param sd        Whether the claim should be selectively disclosable (SD-JWT only)
 */
@Serializable
data class ClaimDescriptionOID4VCI(
    val path: List<String>,
    val mandatory: Boolean = false,
    val sd: Boolean = false,
)

/**
 * Converts an OID4VCI 1.0 claims list into an [SDMap] that walt.id's
 * SD-JWT signing pipeline already understands.
 *
 * Only claims with [ClaimDescriptionOID4VCI.sd] == true are made selectively
 * disclosable; the rest remain as plain-text JWT claims.
 *
 * Nested paths (e.g. ["credentialSubject", "firstName"]) are currently
 * flattened to their leaf key — extend [buildNestedSDMap] if deeper nesting
 * is required.
 */
fun List<ClaimDescriptionOID4VCI>.toSDMap(
    decoyMode: DecoyMode = DecoyMode.NONE,
    decoys: Int = 0,
): SDMap {
    // Group by top-level key so we can build a proper nested SDMap later
    val topLevel = mutableMapOf<String, ClaimDescriptionOID4VCI>()

    for (claim in this) {
        if (claim.path.isEmpty()) continue
        val key = claim.path[0]
        // If path has more than one segment, we need recursion — handled below
        topLevel[key] = claim
    }

    val fields: Map<String, SDField> = topLevel.mapValues { (_, claim) ->
        if (claim.path.size > 1) {
            // Nested: e.g. ["credentialSubject", "firstName"]
            buildNestedSDField(claim)
        } else {
            // Flat: e.g. ["firstName"]
            SDField(sd = claim.sd)
        }
    }

    return fields.toSDMap(decoyMode, decoys)
}

/**
 * Recursively builds an [SDField] with children for nested claim paths.
 */
private fun buildNestedSDField(claim: ClaimDescriptionOID4VCI): SDField {
    return if (claim.path.size <= 1) {
        SDField(sd = claim.sd)
    } else {
        // The current level is NOT sd — only the leaf is
        SDField(
            sd = false,
            children = mapOf(
                claim.path[1] to buildNestedSDField(
                    claim.copy(path = claim.path.drop(1))
                )
            ).toSDMap()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Existing request models (unchanged, kept here for completeness)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class NewSingleCredentialIssuanceRequest(
    val credentialData: W3CVC,
    val credentialConfigurationId: String = credentialData.getType().last(),
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
    /** OID4VCI 1.0 claims array — converted to [selectiveDisclosure] at runtime */
    val claims: List<ClaimDescriptionOID4VCI>? = null,
)

@Serializable
data class IssuerConfiguration(
    val issuerKey: JsonObject,
    val issuerDid: String,
)

@Serializable
data class PinConfiguration(
    val description: String,
    val pin: String? = null,
    val type: TxInputMode = TxInputMode.forPin(pin)
        ?: error("If no pin is provided, pin input `type` has to be provided, allowed values: ${TxInputMode.entries.joinToString()}."),
    val pinLength: Int = pin?.length ?: error("Neither pin nor pin length was supplied"),
    val callbackAuthenticationUrl: String? = null,
) {
    fun toTxCode() = TxCode(type, pinLength, description)

    init {
        require(
            (pin == null && callbackAuthenticationUrl != null)
                    || (pin != null && callbackAuthenticationUrl == null)
        ) { "Either pin directly or authentication URL (and no pin) has to be provided." }
    }
}

@Serializable
data class IssuanceConfiguration(
    val flow: GrantType = GrantType.authorization_code,
    val pin: PinConfiguration? = null,
    val callbackUrl: String? = null,
) {
    init {
        if (pin != null) require(flow == GrantType.pre_authorized_code) {
            "pin argument can only be used for pre-auth issuance flow"
        }
    }
}

@Serializable
data class NewIssuanceRequest(
    val issuer: IssuerConfiguration,
    val issuance: IssuanceConfiguration,
    val credential: List<NewSingleCredentialIssuanceRequest>,
)

@Serializable
data class IssuanceRequest(
    val issuerKey: JsonObject,
    val credentialConfigurationId: String,
    val credentialData: JsonObject? = null,
    val vct: String? = null,
    val mdocData: Map<String, JsonObject>? = null,
    val mapping: JsonObject? = null,

    /**
     * Legacy walt.id selective disclosure format:
     * ```json
     * { "fields": { "FirstName": { "sd": true } }, "decoyMode": "NONE", "decoys": 0 }
     * ```
     * Still supported. Prefer [claims] for new integrations.
     */
    val selectiveDisclosure: SDMap? = null,

    /**
     * OID4VCI 1.0 Final (Appendix B/C) claims array:
     * ```json
     * [
     *   { "path": ["FirstName"], "mandatory": true, "sd": true },
     *   { "path": ["LastName"],  "mandatory": true, "sd": true }
     * ]
     * ```
     * When present, takes precedence over [selectiveDisclosure].
     */
    val claims: List<ClaimDescriptionOID4VCI>? = null,

    val authenticationMethod: AuthenticationMethod? = AuthenticationMethod.PRE_AUTHORIZED,
    val vpRequestValue: String? = null,
    val vpProfile: OpenId4VPProfile? = null,
    val useJar: Boolean? = null,
    val issuerDid: String? = null,
    val x5Chain: List<String>? = null,
    val trustedRootCAs: List<String>? = null,
    var credentialFormat: CredentialFormat? = null,
    val standardVersion: OpenID4VCIVersion? = OpenID4VCIVersion.DRAFT13,
    val display: List<DisplayProperties>? = null,
    val draft11EncodeOfferedCredentialsByReference: Boolean? = true,
    val issuanceType: String? = null,
    val credentialStatus: JsonElement? = null,
    val sdJwtCredentialClaims: JsonObject? = null,
    val mdocStatus: JsonObject? = null,
) {
    /**
     * Returns the effective [SDMap] to use for signing:
     * - If [claims] is provided → convert to SDMap (OID4VCI 1.0 format)
     * - Else fall back to legacy [selectiveDisclosure]
     * - If neither → null (all claims visible)
     */
    val effectiveSelectiveDisclosure: SDMap?
        get() = claims?.toSDMap() ?: selectiveDisclosure

    init {
        credentialData?.let {
            require(it.isNotEmpty()) {
                throw BadRequestException("CredentialData in the request body cannot be empty")
            }
        }
        require(credentialConfigurationId.isNotEmpty()) {
            throw BadRequestException("Credential configuration ID in the request body cannot be empty")
        }
        require(issuerKey.isNotEmpty()) {
            throw BadRequestException("Issuer key in the request body cannot be empty")
        }
    }
}

@Serializable
data class IssuerOnboardingResponse(
    val issuerKey: JsonElement,
    val issuerDid: String,
)
