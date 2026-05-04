package main.java.sbfl;

public enum Formula {

    TARANTULA,
    OCHIAI,
    JACCARD,
    ZOLTAR;

    public static Formula fromString(String name) {
        return switch (name) {
            case "Ochiai" -> OCHIAI;
            case "Jaccard" -> JACCARD;
            case "Zoltar" -> ZOLTAR;
            default -> TARANTULA;
        };
    }
}