package id.sukiapps.hermes.domain.manus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test skill router mini 14.32 + penempelan prompt ke perencana.
 * Murni JVM, tanpa Android, tanpa jaringan.
 */
class SkillRouterTest {

    @Test
    fun debug_dipilih_untukGoalPerbaiki() {
        assertEquals("debug", SkillRouter.pick("perbaiki crash saat login")?.id)
    }

    @Test
    fun audit_dipilih_untukGoalAuditTanpaKataPerbaiki() {
        assertEquals("audit-keamanan", SkillRouter.pick("audit celah keamanan API")?.id)
    }

    @Test
    fun prioritas_perbaiki_menangAtasAuditDanRiset() {
        // "perbaiki" (aksi konkret) didahulukan atas "celah" dan "cari".
        assertEquals("debug", SkillRouter.pick("perbaiki celah keamanan login")?.id)
        assertEquals("debug", SkillRouter.pick("cari bug yang bikin login gagal")?.id)
    }

    @Test
    fun riset_dipilih_untukGoalRiset() {
        assertEquals("riset-web", SkillRouter.pick("riset dokumentasi supabase terbaru")?.id)
    }

    @Test
    fun review_dipilih_untukGoalReview() {
        assertEquals("code-review", SkillRouter.pick("review kode fungsi login")?.id)
    }

    @Test
    fun rencana_dipilih_untukGoalSusunRencana() {
        assertEquals("perencana-tugas", SkillRouter.pick("susun rencana migrasi database")?.id)
    }

    @Test
    fun pencocokan_tidakPekaHurufBesar() {
        assertEquals("debug", SkillRouter.pick("PERBAIKI Crash")?.id)
    }

    @Test
    fun goalUmum_tanpaKataKunci_tidakMemilihSkill() {
        assertNull(SkillRouter.pick("tulis catatan harian"))
        assertNull(SkillRouter.pick(""))
        assertNull(SkillRouter.pick("   "))
    }

    @Test
    fun buildSystemPrompt_tanpaSkill_tidakMenambahPeran() {
        val base = LlmPlanner.buildSystemPrompt(null)
        assertFalse(base.contains("Peran tambahan"))
        assertEquals(base, LlmPlanner.buildSystemPrompt("   "))
    }

    @Test
    fun buildSystemPrompt_denganSkill_menempelDiAkhir() {
        val skill = SkillRouter.pick("perbaiki crash saat login")
        assertTrue(skill != null)
        val full = LlmPlanner.buildSystemPrompt(skill!!.prompt)
        assertTrue(full.contains("Peran tambahan untuk tugas ini:"))
        assertTrue(full.endsWith(skill.prompt))
        assertTrue(full.startsWith(LlmPlanner.buildSystemPrompt(null)))
    }
}
