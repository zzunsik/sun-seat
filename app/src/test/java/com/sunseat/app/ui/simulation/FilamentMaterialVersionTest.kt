package com.sunseat.app.ui.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 미리 컴파일한 3D 재료(`*.filamat`)가 앱에 들어가는 Filament 런타임과 맞는지 확인한다(#195).
 *
 * Filament 런타임은 자기 material 버전과 다른 `.filamat`을 받으면 `Material.Builder.build`에서
 * 네이티브로 종료한다. Robolectric 스크린샷은 3D 대신 자리표시를 찍으므로 이 검사가 없으면
 * CI가 통과하고 실기기에서만 크래시가 보인다. Filament 네이티브 라이브러리는 JVM에서 읽을 수 없어,
 * 런타임 버전(`gradle/libs.versions.toml`) → material 버전을 아래 표로 두고 `.filamat` 헤더와 비교한다.
 * 표에 없는 Filament 버전이면 실패한다. 버전을 올리는 쪽이 재료를 다시 컴파일하고 표를 함께 고치게 하려는 것이다.
 */
class FilamentMaterialVersionTest {
    /**
     * Filament 런타임 버전 → 그 런타임이 받는 material 버전.
     * 값은 각 태그의 `libs/filabridge/include/filament/MaterialEnums.h`의 `MATERIAL_VERSION`이다.
     * (https://github.com/google/filament/blob/v<버전>/libs/filabridge/include/filament/MaterialEnums.h)
     */
    private val materialVersionByFilament = mapOf(
        "1.80.1" to 80,
        "1.81.0" to 81,
    )

    @Test fun everyFilamatMatchesTheBundledFilamentMaterialVersion() {
        val filament = filamentVersion()
        val expected = materialVersionByFilament[filament]
        assertNotNull(
            "gradle/libs.versions.toml의 Filament $filament 에 맞는 material 버전이 표에 없다. " +
                "v$filament 태그의 MaterialEnums.h에서 MATERIAL_VERSION을 확인해 materialVersionByFilament에 넣고, " +
                recompileHint(filament),
            expected,
        )
        val files = filamatFiles()
        assertTrue("app/src/main/assets/simulation 에 .filamat이 없다", files.isNotEmpty())
        val mismatched = files.associate { it.name to chunks(it.readBytes()).getValue("MAT_VERS").uint32() }
            .filterValues { it != expected }
        assertEquals(
            "Filament $filament 런타임은 material 버전 $expected 만 읽는다(다르면 3D 화면 진입 시 네이티브 크래시). " +
                "버전이 다른 재료: $mismatched. " + recompileHint(filament),
            emptyMap<String, Int>(),
            mismatched,
        )
    }

    @Test fun everyMaterialSourceHasACompiledFilamatWithTheSameName() {
        val sources = File("../tools/simulation").listFiles { f -> f.extension == "mat" }.orEmpty()
            .associate { it.nameWithoutExtension to Regex("""\bname\s*:\s*(\w+)""").find(it.readText())!!.groupValues[1] }
        val compiled = filamatFiles().associate { file ->
            file.nameWithoutExtension to chunks(file.readBytes()).getValue("MAT_NAME").cString()
        }
        assertTrue(sources.isNotEmpty())
        assertEquals("tools/simulation/*.mat 과 assets/simulation/*.filamat 이 짝이 맞아야 한다. " + recompileHint(filamentVersion()), sources, compiled)
    }

    @Test fun compileInstructionsNameTheBundledFilamentVersion() {
        // 같은 값이 두 곳에 있으면 어긋날 때 실패하게 둔다(AGENTS.md). README의 matc 버전 = 런타임 버전.
        val filament = filamentVersion()
        val readme = File("../tools/simulation/README.md").readText()
        assertEquals(
            "tools/simulation/README.md의 컴파일 버전이 런타임과 다르다. " + recompileHint(filament),
            setOf(filament),
            Regex("""Filament (\d+\.\d+\.\d+)|releases/tag/v(\d+\.\d+\.\d+)""").findAll(readme)
                .map { m -> m.groupValues.drop(1).first { it.isNotEmpty() } }.toSet(),
        )
    }

    /** 테스트 작업 폴더는 app/이다. */
    private fun filamentVersion(): String =
        Regex("""(?m)^filament\s*=\s*"([^"]+)"""").find(File("../gradle/libs.versions.toml").readText())!!.groupValues[1]

    private fun filamatFiles(): List<File> =
        File("src/main/assets/simulation").listFiles { f -> f.extension == "filamat" }.orEmpty().sortedBy { it.name }

    private fun recompileHint(filament: String) =
        "https://github.com/google/filament/releases/tag/v$filament 의 matc로 tools/simulation/README.md 명령대로 " +
            "app/src/main/assets/simulation/*.filamat 을 모두 다시 컴파일해 같은 PR에 넣는다."

    /**
     * `.filamat`은 청크 목록이다: 태그(uint64 LE, 예 'MAT_VERS'), 크기(uint32 LE), 데이터.
     * 같은 태그가 여러 번 나오면 첫 번째를 쓴다.
     */
    private fun chunks(bytes: ByteArray): Map<String, ByteBuffer> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = LinkedHashMap<String, ByteBuffer>()
        while (buffer.remaining() >= 12) {
            val tag = buffer.long
            val name = String(ByteArray(8) { (tag ushr (8 * (7 - it))).toByte() }, Charsets.US_ASCII)
            val size = buffer.int
            require(size >= 0 && size <= buffer.remaining()) { "잘린 청크 $name" }
            val data = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).limit(size) as ByteBuffer
            result.putIfAbsent(name, data)
            buffer.position(buffer.position() + size)
        }
        assertEquals("청크 끝이 파일 끝과 맞지 않는다", 0, buffer.remaining())
        return result
    }

    private fun ByteBuffer.uint32(): Int = duplicate().order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteBuffer.cString(): String {
        val copy = duplicate()
        val bytes = ByteArray(copy.remaining()).also { copy.get(it) }
        return String(bytes, Charsets.UTF_8).substringBefore('\u0000')
    }
}
