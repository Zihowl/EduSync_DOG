package dev.zihowl.dog.data.session

object RoleMapper {
    fun fromServer(serverRole: String?): String = when (serverRole?.uppercase()) {
        "STUDENT", "ALUMNO" -> SessionManager.ROLE_ALUMNO
        "TEACHER", "DOCENTE" -> SessionManager.ROLE_DOCENTE
        else -> SessionManager.ROLE_UNSUPPORTED
    }
}
