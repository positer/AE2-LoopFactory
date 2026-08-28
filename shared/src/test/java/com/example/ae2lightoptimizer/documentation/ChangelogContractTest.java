package com.example.ae2lightoptimizer.documentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ChangelogContractTest {
    private static final List<Pattern> TIME_INFORMATION = List.of(
            Pattern.compile("\\b(?:19|20)\\d{2}\\b"),
            Pattern.compile("\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b"),
            Pattern.compile("\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}\\b"),
            Pattern.compile("\\b(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?:\\s?(?:am|pm))?\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:january|february|march|april|may|june|july|august|september|"
                    + "october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|"
                    + "mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:today|yesterday|tomorrow|currently|previously|recently)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\d+\\s*[\u5e74\u6708\u65e5\u65f6\u5206\u79d2]"),
            Pattern.compile("(?:\u4eca\u5929|\u6628\u5929|\u660e\u5929|\u661f\u671f[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u65e5\u5929])"));

    @Test
    void containsNoTimeInformation() throws IOException {
        Path changelog = Path.of("..", "..", "CHANGELOG.md").normalize();
        assertTrue(Files.isRegularFile(changelog), "Missing root CHANGELOG.md: " + changelog.toAbsolutePath());
        String content = Files.readString(changelog);

        for (Pattern forbidden : TIME_INFORMATION) {
            var match = forbidden.matcher(content);
            assertFalse(match.find(), () -> "CHANGELOG.md contains forbidden time information '"
                    + match.group() + "' matched by " + forbidden);
        }
    }
}
