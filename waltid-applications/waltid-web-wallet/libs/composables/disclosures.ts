import {decodeBase64ToUtf8, encodeUtf8ToBase64} from "./base64";

export function parseDisclosures(disclosureString: string) {
    try {
        const parts = disclosureString.split("~").filter((elem) => elem && elem.trim().length > 0);
        // Skip first element only if it looks like a JWT (contains dots) — full SD-JWT was passed
        const start = parts.length > 0 && parts[0].includes(".") ? 1 : 0;
        return parts.slice(start).map((elem) => JSON.parse(decodeBase64ToUtf8(elem)));
    } catch (e) {
        console.error("Error parsing disclosures:", e);
        return [];
    }
}

export function encodeDisclosure(disclosure: any[]): string {
    return encodeUtf8ToBase64(JSON.stringify(disclosure)).replaceAll("=", "")
}
