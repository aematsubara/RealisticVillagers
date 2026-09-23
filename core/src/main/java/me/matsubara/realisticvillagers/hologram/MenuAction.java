package me.matsubara.realisticvillagers.hologram;

public enum MenuAction {
    // Main menu
    TALK, TRADE, INTERACTIONS,
    // Talk sub-menu
    CHAT, GREET, STORY, JOKE, INSULT, FLIRT, PROUD_OF,
    // Interactions sub-menu
    ORDER, FOLLOW_ME, STAY_HERE, GIFT, INSPECT_INVENTORY, SET_HOME, COMBAT,
    DIVORCE, DIVORCE_CONFIRM, CANCEL_DIVORCE,
    PROCREATE, INFORMATIONS,
    // Config-defined custom sub-menus (any unrecognised id that matches
    // another "hologram.menus.<id>" list becomes one of these).
    CUSTOM_MENU, CUSTOM_PAGE_PREV, CUSTOM_PAGE_NEXT,
    // Config-only custom chat/conversation type (any unrecognised id that
    // matches a top-level key in messages/default.yml becomes this).
    CUSTOM_CHAT,
    // Config-defined custom chest GUI (any unrecognised id that matches
    // a "gui.custom.<id>" section in gui.yml becomes this).
    CUSTOM_GUI,
    // Mayor screens
    MAYOR_RADAR, MAYOR_STORAGE, MAYOR_COMMISSION, MAYOR_BORDERS, MAYOR_RENAME,
    RADAR_TOGGLE_RESIDENTS, RADAR_TOGGLE_MAYOR, RADAR_ZOOM_IN, RADAR_ZOOM_OUT,
    // Info panel navigation (left side)
    INFO_PREV, INFO_NEXT, CHILDREN_PREV, CHILDREN_NEXT,
    // Navigation
    BACK
}
