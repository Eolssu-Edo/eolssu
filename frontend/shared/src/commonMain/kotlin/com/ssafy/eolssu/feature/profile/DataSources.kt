package com.ssafy.eolssu.feature.profile

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.source_cat_audio_name
import eolssu.shared.generated.resources.source_cat_audio_note
import eolssu.shared.generated.resources.source_cat_audio_usage
import eolssu.shared.generated.resources.source_daegeum_book_name
import eolssu.shared.generated.resources.source_daegeum_book_note
import eolssu.shared.generated.resources.source_daegeum_book_usage
import eolssu.shared.generated.resources.source_eacademy_name
import eolssu.shared.generated.resources.source_eacademy_note
import eolssu.shared.generated.resources.source_eacademy_usage
import eolssu.shared.generated.resources.source_generator_name
import eolssu.shared.generated.resources.source_generator_note
import eolssu.shared.generated.resources.source_generator_usage
import eolssu.shared.generated.resources.source_gugak_audio_name
import eolssu.shared.generated.resources.source_gugak_audio_note
import eolssu.shared.generated.resources.source_gugak_audio_usage
import eolssu.shared.generated.resources.source_kind_audio
import eolssu.shared.generated.resources.source_kind_book
import eolssu.shared.generated.resources.source_kind_education
import eolssu.shared.generated.resources.source_kind_image_data
import eolssu.shared.generated.resources.source_kind_score_book
import eolssu.shared.generated.resources.source_link_kogl
import eolssu.shared.generated.resources.source_link_original
import eolssu.shared.generated.resources.source_link_repository
import eolssu.shared.generated.resources.source_sanjo_chart_name
import eolssu.shared.generated.resources.source_sanjo_chart_usage
import eolssu.shared.generated.resources.source_sogeum_book_name
import eolssu.shared.generated.resources.source_sogeum_book_usage
import org.jetbrains.compose.resources.StringResource

/**
 * 앱 화면에서 쓰는 자료·데이터의 출처 하나. 같은 출처를 여러 화면이 써도 여기에는 한 번만 둔다([usage]에 쓰는 곳을 모은다).
 *
 * @property links 원본·이용 조건 주소. 직접 열어 자료와 맞는지 확인한 주소만 넣고, 확인하지 못한 자료는 비워 둔다(화면에 링크 칸이 생기지 않는다).
 */
data class DataSource(
    val id: String,
    val name: StringResource,
    val kind: StringResource,
    val usage: StringResource,
    val note: StringResource? = null,
    val links: List<DataSourceLink> = emptyList(),
)

data class DataSourceLink(val label: StringResource, val url: String)

/**
 * 마이페이지 '자료 및 데이터 출처'에 보여 주는 출처 목록. 새 출처는 이 목록에 항목 하나와 `data_sources.xml` 문구를 더한다.
 * 라이선스 전문(시김새 그림 MIT · 글꼴 OFL)은 `composeResources/files/licenses/`, 장단 음원 이용 조건은 `files/jangdan/ASSET_LICENSE.md`에 그대로 둔다.
 */
val DataSources: List<DataSource> = listOf(
    DataSource(
        id = "gugak-digital-audio",
        name = Res.string.source_gugak_audio_name,
        kind = Res.string.source_kind_audio,
        usage = Res.string.source_gugak_audio_usage,
        note = Res.string.source_gugak_audio_note,
        links = listOf(
            DataSourceLink(Res.string.source_link_original, "https://www.gugak.go.kr/digitaleum/front/monotone/list.do"),
            DataSourceLink(Res.string.source_link_kogl, "https://www.kogl.or.kr/info/license.do"),
        ),
    ),
    // CAT 이용 안내는 2차 저작물에 지정 문구 표시를 요구한다. 문구는 data/src/playback/sampler.py의 CREDIT과 같게 둔다.
    DataSource(
        id = "cat-gugak-library",
        name = Res.string.source_cat_audio_name,
        kind = Res.string.source_kind_audio,
        usage = Res.string.source_cat_audio_usage,
        note = Res.string.source_cat_audio_note,
        links = listOf(DataSourceLink(Res.string.source_link_original, "https://www.catseoul.com/gugak-library/")),
    ),
    DataSource(
        id = "jeongganbo-generator",
        name = Res.string.source_generator_name,
        kind = Res.string.source_kind_image_data,
        usage = Res.string.source_generator_usage,
        note = Res.string.source_generator_note,
        links = listOf(DataSourceLink(Res.string.source_link_repository, "https://github.com/danbinaerinHan/jeongganbo-generator")),
    ),
    DataSource(
        id = "gugak-eacademy",
        name = Res.string.source_eacademy_name,
        kind = Res.string.source_kind_education,
        usage = Res.string.source_eacademy_usage,
        note = Res.string.source_eacademy_note,
        links = listOf(DataSourceLink(Res.string.source_link_original, "https://academy.gugak.go.kr/")),
    ),
    DataSource(
        id = "gugak-sogeum-book",
        name = Res.string.source_sogeum_book_name,
        kind = Res.string.source_kind_book,
        usage = Res.string.source_sogeum_book_usage,
        // 국립국악원 악기연구소 게시판. 「3장. 소금」 PDF(kor_sogeum.pdf)가 이 목록에 있다.
        links = listOf(
            DataSourceLink(
                Res.string.source_link_original,
                "https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=138&menuid=001003003006002&lang=ko",
            ),
        ),
    ),
    DataSource(
        id = "gugak-daegeum-jeongak-book",
        name = Res.string.source_daegeum_book_name,
        kind = Res.string.source_kind_score_book,
        usage = Res.string.source_daegeum_book_usage,
        note = Res.string.source_daegeum_book_note,
        links = listOf(
            DataSourceLink(
                Res.string.source_link_original,
                "https://www.gugak.go.kr/site/program/board/basicboard/view?boardid=17409&boardtypeid=88&menuid=001003002002&pagesize=10",
            ),
        ),
    ),
    DataSource(
        id = "pirisa-sanjo-chart",
        name = Res.string.source_sanjo_chart_name,
        kind = Res.string.source_kind_education,
        usage = Res.string.source_sanjo_chart_usage,
        links = listOf(DataSourceLink(Res.string.source_link_original, "https://kukaksa.co.kr/web/myimg/daekum%20use.jpg")),
    ),
)
