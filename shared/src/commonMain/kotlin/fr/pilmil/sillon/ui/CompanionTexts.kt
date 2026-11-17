package fr.pilmil.sillon.ui

import androidx.compose.runtime.Composable
import fr.pilmil.sillon.model.CompanionRelation
import fr.pilmil.sillon.model.EvidenceLevel
import fr.pilmil.sillon.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Libellé du niveau de preuve, dans la langue de l'appli. */
internal fun evidenceLabelRes(level: EvidenceLevel): StringResource = when (level) {
    EvidenceLevel.ETAYE -> Res.string.evidence_etaye
    EvidenceLevel.PLAUSIBLE -> Res.string.evidence_plausible
    EvidenceLevel.TRADITION -> Res.string.evidence_tradition
}

/**
 * Corps de la phrase d'une relation de la fiche d'un légume ; le paramètre est le détail (justification ou renvoi).
 * Null : la justification de la fiche est reprise telle quelle. Même logique que [CompanionRelation.noteFor].
 */
internal fun companionBodyRes(relation: CompanionRelation, fromTheirSheet: Boolean): StringResource? = when {
    relation.isHarmful && relation.evidence == EvidenceLevel.TRADITION -> Res.string.companion_body_bad_tradition
    relation.isHarmful && relation.evidence == EvidenceLevel.PLAUSIBLE -> Res.string.companion_body_bad_plausible
    relation.isHarmful && fromTheirSheet -> Res.string.companion_body_bad_other
    relation.evidence == EvidenceLevel.TRADITION -> Res.string.companion_body_good_tradition
    fromTheirSheet -> Res.string.companion_body_good_other
    else -> null
}

/**
 * Phrase de la fiche d'un légume pour cette relation, vers [targetName]. Une simple tradition ou une
 * idée plausible n'est pas présentée comme un fait ; le niveau de preuve est écrit en toutes lettres.
 * [fromTheirSheet] : la relation est décrite sur la fiche de l'autre plante, son texte parle donc d'elle.
 * La justification elle-même vient du catalogue (en français).
 */
@Composable
internal fun CompanionRelation.note(targetName: String, fromTheirSheet: Boolean = false): String {
    val bodyRes = companionBodyRes(this, fromTheirSheet)
    val body = if (bodyRes == null) {
        rationale.trimEnd('.')
    } else {
        val detail = if (fromTheirSheet) {
            stringResource(Res.string.companion_detail_their_sheet)
        } else {
            stringResource(Res.string.companion_detail_rationale, rationale.trimEnd('.'))
        }
        stringResource(bodyRes, detail)
    }
    return stringResource(Res.string.companion_note, targetName, body, stringResource(evidenceLabelRes(evidence))) +
        (source?.let { " ($it)" } ?: "")
}
