package ru.stepenko.rootme.model.person

enum class Relationship(val desc: String) {
    // 5 поколение
    GREAT_GREAT_GRANDSON("Праправнук"),
    GREAT_GREAT_GRANDDAUGHTER("Праправнучка"),

    // 4 поколение
    GREAT_GRANDSON("Правнук"),
    GREAT_GRANDDAUGHTER("Правнучка"),

    // 3 поколение
    GRANDSON("Внук"),
    GRANDDAUGHTER("Внучка"),

    // 2 поколение
    SON("Сын"),
    DAUGHTER("Дочь"),

    // 1 поколение
    YOU("Вы"),
    BROTHER("Брат"),
    SISTER("Сестра"),
    HUSBAND("Муж"),
    WIFE("Жена"),

    // -1 поколение
    FATHER("Отец"),
    MOTHER("Мать"),
    UNCLE("Дядя"),
    AUNT("Тётя"),

    // -2 поколение
    GRANDFATHER("Дедушка"),
    GRANDMOTHER("Бабушка"),

    // -3 поколение
    GREAT_GRANDFATHER("Прадедушка"),
    GREAT_GRANDMOTHER("Прабабушка"),

    // -4 поколение
    GREAT_GREAT_GRANDFATHER("Прапрадедушка"),
    GREAT_GREAT_GRANDMOTHER("Прапрабабушка"),

    // -5 поколение
    GREAT_GREAT_GREAT_GRANDFATHER("Прапрапрадедушка"),
    GREAT_GREAT_GREAT_GRANDMOTHER("Прапрапрабабушка"),
    NONE("Степень родства пока не поддерживается")
}