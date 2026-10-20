import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sap.jw.util.CourseSections;
import edu.csuft.sap.data.schedule.SchoolSections;
import edu.csuft.sap.data.schedule.WeekUtil;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Offline replay: runs production parsers against server-grouped source formats.
 * The independent oracle deliberately accepts only numeric lists/ranges, the
 * complete grammar observed in this selected batch. Other syntax is flagged for
 * manual review instead of silently copying the production parser's assumptions.
 */
public final class ClassSourceParserAudit {
    private static final Pattern NUMERIC_LIST = Pattern.compile("[0-9]{1,2}(?:-[0-9]{1,2})?(?:,[0-9]{1,2}(?:-[0-9]{1,2})?)*");

    static List<Integer> numericReference(String value, int maximum) {
        if (value == null || !NUMERIC_LIST.matcher(value).matches()) return null;
        var result = new TreeSet<Integer>();
        for (String part : value.split(",")) {
            String[] ends = part.split("-");
            int start = Integer.parseInt(ends[0]);
            int end = Integer.parseInt(ends[ends.length - 1]);
            if (start < 1 || end < start || end > maximum) return null;
            for (int i = start; i <= end; i++) result.add(i);
        }
        return List.copyOf(result);
    }

    static int referenceDay(String value) {
        return switch (Objects.toString(value, "")) {
            case "星期一" -> 1; case "星期二" -> 2; case "星期三" -> 3;
            case "星期四" -> 4; case "星期五" -> 5; case "星期六" -> 6;
            case "星期日" -> 7; default -> 0;
        };
    }

    static boolean sameStoredText(String a, String b) {
        return Objects.toString(a, "").trim().equals(Objects.toString(b, "").trim());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("INPUT_GROUPED_JSON OUTPUT_REPLAY_JSON required");
        JSONObject input = JSON.parseObject(Files.readString(Path.of(args[0])));
        Class<?> service = Class.forName("com.sap.jw.service.ClassScheduleService");
        Method dayParser = service.getDeclaredMethod("dayOf", String.class);
        dayParser.setAccessible(true);
        Map<String, List<Integer>> parsedWeeks = new LinkedHashMap<>();
        Map<String, List<Integer>> backendSections = new LinkedHashMap<>();
        Map<String, List<Integer>> appSections = new LinkedHashMap<>();
        Map<String, Integer> parsedDays = new LinkedHashMap<>();
        Map<String, Long> issueCounts = new TreeMap<>();
        List<JSONObject> findings = new ArrayList<>();
        long checkedRows = 0, affectedRows = 0, longSectionRows = 0, weekendRows = 0;
        int groups = 0;
        JSONObject sources = input.getJSONObject("sources");
        for (String source : sources.keySet()) {
            JSONArray formats = sources.getJSONObject(source).getJSONArray("formats");
            long sourceChecked = 0;
            for (int i = 0; i < formats.size(); i++) {
                JSONObject format = formats.getJSONObject(i);
                long count = format.getLongValue("count");
                checkedRows += count; sourceChecked += count; groups++;
                String weeks = format.getString("weeks"), section = format.getString("section"), day = format.getString("day");
                List<Integer> w = parsedWeeks.computeIfAbsent(weeks, key -> WeekUtil.INSTANCE.parseWeeks(key));
                List<Integer> b = backendSections.computeIfAbsent(section, CourseSections::parse);
                List<Integer> a = appSections.computeIfAbsent(section, key -> SchoolSections.INSTANCE.parse(key));
                if (!parsedDays.containsKey(day)) parsedDays.put(day, (Integer) dayParser.invoke(null, day));
                List<String> errors = new ArrayList<>();
                if (format.getIntValue("raw_valid") != 1) errors.add("invalid_source_json");
                if (!sameStoredText(weeks, format.getString("raw_weeks")) || !sameStoredText(section, format.getString("raw_section")) || !sameStoredText(day, format.getString("raw_day"))) errors.add("source_column_mismatch");
                List<Integer> expectedWeeks = numericReference(format.getString("raw_weeks"), 60);
                List<Integer> expectedSections = numericReference(format.getString("raw_section"), 16);
                if (expectedWeeks == null) errors.add("week_syntax_requires_manual_review");
                else if (!expectedWeeks.equals(w)) errors.add("incorrect_or_partial_week_expansion");
                if (w.isEmpty() && !WeekUtil.INSTANCE.isUnrestricted(weeks)) errors.add("unparseable_week");
                if (WeekUtil.INSTANCE.isUnrestricted(weeks)) errors.add("unrestricted_week_requires_review");
                if (expectedSections == null) errors.add("section_syntax_requires_manual_review");
                else if (!expectedSections.equals(b)) errors.add("incorrect_backend_section_expansion");
                if (b.isEmpty()) errors.add("unparseable_section");
                if (!a.equals(b)) errors.add("backend_app_section_disagreement");
                int expectedDay = referenceDay(format.getString("raw_day"));
                if (expectedDay == 0 || parsedDays.get(day) != expectedDay) errors.add("incorrect_or_unparseable_weekday");
                if (b.size() > 2) longSectionRows += count;
                if (parsedDays.get(day) >= 6) weekendRows += count;
                if (!errors.isEmpty()) {
                    affectedRows += count;
                    for (String error : errors) issueCounts.merge(error, count, Long::sum);
                    JSONObject finding = new JSONObject(format);
                    finding.put("source", source); finding.put("issues", errors);
                    finding.put("parsedWeeks", w); finding.put("backendSections", b);
                    finding.put("appSections", a); finding.put("parsedDay", parsedDays.get(day));
                    findings.add(finding);
                }
            }
            if (sourceChecked != sources.getJSONObject(source).getLongValue("row_count")) throw new IllegalStateException("Grouped source row count is incomplete");
        }
        JSONObject result = new JSONObject();
        result.put("selection", input.get("selection")); result.put("snapshot", input.get("clock"));
        result.put("checkedRows", checkedRows); result.put("checkedGroups", groups);
        result.put("uniqueWeeks", parsedWeeks.size()); result.put("uniqueSections", backendSections.size());
        result.put("uniqueDays", parsedDays.size()); result.put("affectedRows", affectedRows);
        result.put("issueCounts", issueCounts); result.put("findings", findings);
        result.put("longSectionRows", longSectionRows); result.put("weekendRows", weekendRows);
        result.put("parsedWeeks", parsedWeeks); result.put("backendSections", backendSections);
        result.put("appSections", appSections); result.put("parsedDays", parsedDays);
        result.put("scopeLimit", "Only persisted rows in the selected term snapshot; cannot detect missing source pages or rows discarded before persistence.");
        Files.writeString(Path.of(args[1]), JSON.toJSONString(result), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.printf("Checked %,d rows (%d groups): %d week forms, %d section forms, %d day forms; affected rows: %,d%n",
                checkedRows, groups, parsedWeeks.size(), backendSections.size(), parsedDays.size(), affectedRows);
    }
}
