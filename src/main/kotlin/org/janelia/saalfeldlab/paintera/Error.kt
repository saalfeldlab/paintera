package org.janelia.saalfeldlab.paintera

private const val ERROR_CODE_1 = "No Paintera project specified"
private const val ERROR_CODE_2 = "Unable to deserialize Paintera project"

enum class Error(val code: Int, val description: String) {
    NO_PROJECT_SPECIFIED(1, ERROR_CODE_1),
    UNABLE_TO_DESERIALIZE_PROJECT(2, ERROR_CODE_2);
}
