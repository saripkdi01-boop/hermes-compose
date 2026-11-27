package id.sukiapps.hermes.data.local

/** v20.1 — hasil query [HermesDao.searchMessages] (POJO, bukan tabel). */
data class MessageHit(
    val msgId: Long,
    val convId: Long,
    val title: String,
    val role: String,
    val content: String,
    val createdAt: Long
)
