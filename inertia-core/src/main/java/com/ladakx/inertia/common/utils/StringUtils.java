package com.ladakx.inertia.common.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.intellij.lang.annotations.RegExp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for string manipulation.
 */
public class StringUtils {

    /**
     * The lower case alphabet.
     */
    public static final String LOWER_ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    /**
     * The upper case alphabet.
     */
    public static final String VALID_HEX = "0123456789AaBbCcDdEeFf";
    /**
     * The valid Minecraft color codes.
     */
    public static final String MINECRAFT_COLOR_CODES = VALID_HEX + "KkLlMmNnOoRrXx";

    /**
     * The suffixes for ordinal numbers.
     */
    private static final String[] SUFFIXES = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};

    private StringUtils () {
        // utility class
    }

    /**
     * Counts the occurrences of a character in a string.
     * @param str The string to count the occurrences in.
     * @param ch The character to count.
     * @return The number of occurrences.
     */
    public static int countOccurrences(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    /**
     * Repeats a string a number of times.
     * @param str The string to repeat.
     * @param times The number of times to repeat the string.
     * @return The repeated string.
     */
    public static String repeat(String str, int times) {
        if (str.isEmpty() || times <= 0) {
            return "";
        }
        return str.repeat(times);
    }

    /**
     * Repeats a character a number of times.
     * @param str The character to repeat.
     * @param index The number of times to repeat the character.
     * @param includeBackslash Whether to include the backslash in the count.
     * @return Whether the character is escaped.
     */
    public static boolean isEscaped(String str, int index, boolean includeBackslash) {
        if (index == 0) {
            return false;
        }

        int backslashes = 0;
        for (int i = index - 1; i >= 0; i--) {
            if (str.charAt(i) != '\\') {
                break;
            }
            backslashes++;
        }

        return backslashes % 2 == 1 || (!includeBackslash && str.charAt(index) == '\\');
    }

    /**
     * Matches a string with a regex pattern.
     * @param regex The regex pattern to match.
     * @param str The string to match.
     * @return The matched string.
     */
    public static String match(@RegExp String regex, String str) {
        return match(Pattern.compile(regex), str);
    }

    public static String match(Pattern regex, String str) {
        Matcher matcher = regex.matcher(str);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public static String colorBukkit(String str) {
        StringBuilder result = new StringBuilder(str.length());

        int i = 0;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c != '&') {
                result.append(c);
            } else if (i != 0 && str.charAt(i - 1) == '\\') {
                result.setCharAt(result.length() - 1, '&');
            } else if (i + 1 != str.length()) {
                if (MINECRAFT_COLOR_CODES.indexOf(str.charAt(i + 1)) != -1) {
                    result.append('§');
                } else if (str.charAt(i + 1) == '#') {
                    int bound = i + 7;
                    if (bound <= str.length()) {
                        result.append('§').append('x');
                        i += 2;
                        while (i <= bound) {
                            result.append('§').append(str.charAt(i));
                            i++;
                        }
                        i--;
                    }
                } else {
                    result.append('&');
                }
            } else {
                result.append('&');
            }
            i++;
        }

        return result.toString();
    }

    /**
     * Converts "Legacy" color codes (&c,&6,&l) to new ones (MiniMessage).
     *
     * @param value The string to convert.
     * @return The converted string.
     */
    public static String colorAdventure(String value) {
        value = value.replace('§', '&');

        Map<String, String> replacements = new HashMap<>();
        replacements.put("&0", "<black>");
        replacements.put("&1", "<dark_blue>");
        replacements.put("&2", "<dark_green>");
        replacements.put("&3", "<dark_aqua>");
        replacements.put("&4", "<dark_red>");
        replacements.put("&5", "<dark_purple>");
        replacements.put("&6", "<gold>");
        replacements.put("&7", "<gray>");
        replacements.put("&8", "<dark_gray>");
        replacements.put("&9", "<blue>");
        replacements.put("&[aA]", "<green>");
        replacements.put("&[bB]", "<aqua>");
        replacements.put("&[cC]", "<red>");
        replacements.put("&[dD]", "<light_purple>");
        replacements.put("&[eE]", "<yellow>");
        replacements.put("&[fF]", "<white>");
        replacements.put("&[kK]", "<obfuscated>");
        replacements.put("&[lL]", "<bold>");
        replacements.put("&[mM]", "<strikethrough>");
        replacements.put("&[nN]", "<underline>");
        replacements.put("&[oO]", "<italic>");
        replacements.put("&[rR]", "<reset>");

        Pattern regex = Pattern.compile("&#([a-f]|[A-F]|\\d){6}");
        Matcher matcher = regex.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group(0);
            String replacement = "<" + match.substring(1) + ">";
            matcher.appendReplacement(builder, replacement);
        }
        matcher.appendTail(builder);
        value = builder.toString();

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replaceAll(entry.getKey(), entry.getValue());
        }

        return value;
    }

    public static String ordinal(int number) {
        if (number % 100 >= 11 && number % 100 <= 13) {
            return number + "th";
        } else {
            return number + SUFFIXES[number % 10];
        }
    }

    public static List<String> splitCapitalLetters(String str) {
        return List.of(str.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));
    }

    public static List<String> splitAfterWord(String str) {
        return List.of(str.split("(?!\\S+) |(?!\\S+)")).stream().filter(s -> !s.isBlank()).toList();
    }

    public static List<String> split(String str) {
        boolean addDash = false;
        if (str.startsWith("-")) {
            addDash = true;
            str = str.substring(1);
        }
        List<String> split = List.of(str.split("[~ ]+|(?<![~ -])-"));
        if (addDash) {
            split.set(0, "-" + split.get(0));
        }
        return split;
    }

    public static String camelToSnake(String camel) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i != 0 && Character.isUpperCase(c)) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    public static String snakeToUpperSnake(String snake) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (i == 0 || snake.charAt(i - 1) == '_') {
                builder.append(Character.toUpperCase(c));
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    public static String snakeToReadable(String snake) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                builder.append(' ');
            } else if (i == 0 || snake.charAt(i - 1) == '_') {
                builder.append(Character.toUpperCase(c));
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    public static String didYouMean(String input, Iterable<String> possibilities) {
        String closest = null;
        int closestDistance = Integer.MAX_VALUE;
        int[] table = toCharTable(input);

        for (String possibility : possibilities) {
            int[] localTable = toCharTable(possibility);
            int localDifference = Math.abs(input.length() - possibility.length());

            for (int i = 0; i < table.length; i++) {
                localDifference += Math.abs(table[i] - localTable[i]);
            }

            if (localDifference < closestDistance) {
                closest = possibility;
                closestDistance = localDifference;
            }
        }

        if (closest == null) {
            throw new IllegalArgumentException("You passed 0 possibilities to the didYouMean function.");
        }
        return closest;
    }

    public static int[] toCharTable(String str) {
        str = str.toLowerCase();
        int[] table = new int[LOWER_ALPHABET.length()];
        for (char c : str.toCharArray()) {
            int index = c - 'a';
            if (index >= 0 && index < table.length) {
                table[index]++;
            }
        }
        return table;
    }

    /**
     * Converts a Component to a String.
     * @param component The Component to be converted.
     * @return The converted String.
     */
    public static String parseComponent(Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    /**
     * Converts a String to a Component.
     * @param string The String to be converted.
     * @return The converted Component.
     */
    public static Component parseString(String string) {
        if (string == null) return null;
        return MiniMessage.miniMessage().deserialize(colorAdventure(string));
    }

    /**
     * Converts a List<String> to a List<Component>.
     * @param string The List<String> to be converted.
     * @return The converted List<Component>.
     */
    public static List<Component> parseStringList(List<String> string) {
        if (string == null) return null;
        List<Component> result = new ArrayList<>();

        for (String s : string) {
            result.add(MiniMessage.miniMessage().deserialize(colorAdventure(s)));
        }

        return result;
    }


    /**
     * Replaces text in a MiniMessage component with other text.
     *
     * @param text The component to replace text in.
     * @return The component with text replaced.
     */
    public static Component replace(Component text, String... replacement) {
        if (replacement.length % 2 != 0) {
            throw new IllegalArgumentException("The array must have an even number of elements.");
        }

        String str = MiniMessage.miniMessage().serialize(text);

        for (int i = 0; i < replacement.length; i += 2) {
            String key = replacement[i];
            String value = replacement[i + 1];
            str = str.replace(key, value);
        }

        return MiniMessage.miniMessage().deserialize(str);
    }
}
