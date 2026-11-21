package pe.redactcode.nucleo.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReferenciaSha(val sha: String)

@Serializable
data class ReferenciaGit(val ref: String = "", @SerialName("object") val objeto: ReferenciaSha)

@Serializable
data class CommitGit(val sha: String, @SerialName("tree") val arbol: ReferenciaSha, val message: String = "", val parents: List<ReferenciaSha> = emptyList())

@Serializable
data class EntradaArbolGit(val path: String, val mode: String, val type: String, val sha: String? = null, val size: Long? = null)

@Serializable
data class ArbolGit(val sha: String, val tree: List<EntradaArbolGit>, val truncated: Boolean = false) {
    val archivos: Map<String, String> get() = tree.filter { it.type == "blob" && it.sha != null }.associate { it.path to it.sha!! }
}

@Serializable
data class BlobGit(val sha: String = "", val content: String, val encoding: String)

@Serializable
data class NuevoBlob(val content: String, val encoding: String)

@Serializable
data class EntradaNuevoArbol(val path: String, val mode: String = "100644", val type: String = "blob", val sha: String?)

@Serializable
data class AutorGit(val name: String, val email: String)

@Serializable
data class NuevoCommit(val message: String, val tree: String, val parents: List<String>, val author: AutorGit? = null)

@Serializable
data class ActualizarReferencia(val sha: String, val force: Boolean = false)

@Serializable
data class NuevaReferencia(val ref: String, val sha: String)

@Serializable
data class AutorCommitGitHub(val name: String = "", val email: String = "", val date: String = "")

@Serializable
data class DetalleCommitGitHub(val message: String = "", val author: AutorCommitGitHub? = null)

@Serializable
data class ResumenCommitGitHub(val sha: String, val commit: DetalleCommitGitHub, @SerialName("html_url") val url: String? = null)
