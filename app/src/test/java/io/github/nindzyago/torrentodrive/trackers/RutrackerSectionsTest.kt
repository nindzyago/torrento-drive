package io.github.nindzyago.torrentodrive.trackers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RutrackerSectionsTest {
    @Test fun filmsSeriesCartoonsAreOnByDefault() {
        assertTrue(RutrackerWeb.isVideo("Кино, Видео и ТВ", "Зарубежное кино"))
        assertTrue(RutrackerWeb.isVideo("Кино, Видео и ТВ", "Наше кино"))
        assertTrue(RutrackerWeb.isVideo("Сериалы", "Зарубежные сериалы"))
        assertTrue(RutrackerWeb.isVideo("Кино, Видео и ТВ", "Мультфильмы"))
        assertTrue(RutrackerWeb.isVideo("Документалистика и юмор", "Документальные фильмы"))
    }

    @Test fun copiesAndOtherGroupsAreOff() {
        assertFalse(RutrackerWeb.isVideo("Кино, Видео и ТВ", "3D/Стерео Кино, Видео, TV и Спорт"))
        assertFalse(RutrackerWeb.isVideo("Кино, Видео и ТВ", "DVD Video"))
        assertFalse(RutrackerWeb.isVideo("Книги и журналы", "Кино, театр, ТВ, мультипликация, цирк"))
        assertFalse(RutrackerWeb.isVideo("Игры", "Игры для Windows"))
        assertFalse(RutrackerWeb.isVideo("", ""))
    }
}
