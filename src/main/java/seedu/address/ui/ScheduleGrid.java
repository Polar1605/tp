package seedu.address.ui;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import seedu.address.commons.core.LogsCenter;
import seedu.address.model.person.Person;
import seedu.address.model.schedule.ScheduledSession;

/**
 * A weekly timetable with one row per day and time running left to right. Each session is a card that starts
 * and ends at its exact times, except that a session shorter than an hour gets a one-hour card so its details
 * stay readable. Overlapping cards on the same day are stacked in extra lanes, which makes that day's row taller.
 * Details too long for one line wrap, and that day's row grows so every card shows in full.
 * The viewport shows five days and {@code VISIBLE_HOURS} hours; the rest is reached by scrolling.
 * The day labels and time header stay pinned to the viewport edges.
 */
class ScheduleGrid extends Pane {

    private static final int HEADER_HEIGHT = 30;
    private static final int AXIS_WIDTH = 60;
    private static final int MIN_HOUR_WIDTH = 120;
    private static final int MIN_ROW_HEIGHT = 90;
    private static final int MINUTES_IN_HOUR = 60;
    private static final int MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR;
    // Shorter sessions get a card this long, as narrower cards are too small to show their details.
    private static final int MIN_CARD_MINUTES = 60;
    // Keeps content slightly smaller than the viewport so scrollbars do not flicker on and off.
    private static final int FIT_MARGIN = 2;
    private static final int DAYS = 7;
    private static final int VISIBLE_DAYS = 5;
    private static final int VISIBLE_START_HOUR = 10;
    private static final int VISIBLE_END_HOUR = 18;
    private static final String BACKGROUND_COLOUR = "#2b2b2b";
    private static final String LINE_COLOUR = "#555555";

    private final Logger logger = LogsCenter.getLogger(ScheduleGrid.class);
    private final List<Placement> placements = new ArrayList<>();
    private final List<Label> dayLabels = new ArrayList<>();
    private final List<Label> hourLabels = new ArrayList<>();
    private final List<Line> hourLines = new ArrayList<>();
    private final List<Line> dayLines = new ArrayList<>();
    private final Region corner = new Region();
    private final int[] dayLanes = new int[DAYS];
    private double viewportWidth;
    private double viewportHeight;
    private double offsetX;
    private double offsetY;
    private int rangeStartHour = VISIBLE_START_HOUR;
    private int rangeEndHour = VISIBLE_END_HOUR;

    ScheduleGrid() {
        setStyle("-fx-background-color: " + BACKGROUND_COLOUR + ";");
        corner.setStyle("-fx-background-color: " + BACKGROUND_COLOUR + ";");
    }

    /** Sets the visible size, which determines how large the columns and rows are. */
    void setViewport(double width, double height) {
        viewportWidth = width;
        viewportHeight = height;
        requestLayout();
    }

    /** Sets how far the viewport is scrolled, so pinned labels can follow it. */
    void setScrollOffsets(double x, double y) {
        offsetX = x;
        offsetY = y;
        requestLayout();
    }

    /** Returns the width of one hour on the timeline. */
    double hourWidth() {
        double visibleHours = VISIBLE_END_HOUR - VISIBLE_START_HOUR;
        return Math.max(MIN_HOUR_WIDTH, Math.floor((viewportWidth - AXIS_WIDTH) / visibleHours) - FIT_MARGIN);
    }

    /** Returns the standard height of one lane in a day row. A day's lanes grow taller if a card needs more room. */
    double rowHeight() {
        return Math.max(MIN_ROW_HEIGHT, Math.floor((viewportHeight - HEADER_HEIGHT) / VISIBLE_DAYS) - FIT_MARGIN);
    }

    /**
     * Rebuilds the timetable from {@code sessions}, colouring each card using {@code colourOf}
     * and heading it with {@code titleOf}.
     */
    void setSessions(List<ScheduledSession> sessions, Function<Person, String> colourOf,
            Function<Person, String> titleOf) {
        getChildren().clear();
        placements.clear();
        dayLabels.clear();
        hourLabels.clear();
        hourLines.clear();
        dayLines.clear();
        Arrays.fill(dayLanes, 1);
        Map<String, Integer> sessionNumbers = new HashMap<>();
        for (int i = 0; i < sessions.size(); i++) {
            sessionNumbers.put(sessions.get(i).getSessionId(), i + 1);
        }

        int minStart = VISIBLE_START_HOUR * MINUTES_IN_HOUR;
        int maxEnd = VISIBLE_END_HOUR * MINUTES_IN_HOUR;
        for (ScheduledSession session : sessions) {
            minStart = Math.min(minStart, startMinutes(session));
            maxEnd = Math.max(maxEnd, cardEndMinutes(session));
        }
        rangeStartHour = minStart / MINUTES_IN_HOUR;
        rangeEndHour = (maxEnd + MINUTES_IN_HOUR - 1) / MINUTES_IN_HOUR;

        for (int hour = rangeStartHour; hour <= rangeEndHour; hour++) {
            Label label = new Label(String.format("%02d00", hour));
            label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 10px; -fx-background-color: "
                    + BACKGROUND_COLOUR + ";");
            label.setAlignment(Pos.CENTER_LEFT);
            hourLabels.add(label);
            Line line = new Line();
            line.setStyle("-fx-stroke: " + LINE_COLOUR + ";");
            hourLines.add(line);
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            Label label = new Label(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH));
            label.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-background-color: "
                    + BACKGROUND_COLOUR + ";");
            label.setAlignment(Pos.CENTER);
            dayLabels.add(label);
            Line line = new Line();
            line.setStyle("-fx-stroke: " + LINE_COLOUR + ";");
            dayLines.add(line);
        }
        getChildren().addAll(hourLines);
        getChildren().addAll(dayLines);

        for (DayOfWeek day : DayOfWeek.values()) {
            List<ScheduledSession> daySessions = sessions.stream()
                    .filter(s -> s.getDay() == day)
                    .sorted(Comparator.comparingInt(ScheduleGrid::startMinutes)
                            .thenComparingInt(ScheduleGrid::endMinutes))
                    .toList();
            placeDay(day.getValue() - 1, daySessions, colourOf, titleOf,
                    session -> sessionNumbers.get(session.getSessionId()));
        }

        getChildren().addAll(hourLabels);
        getChildren().addAll(dayLabels);
        getChildren().add(corner);
        requestLayout();
    }

    private static int startMinutes(ScheduledSession session) {
        LocalTime start = session.getTimeSlot().start;
        return start.getHour() * MINUTES_IN_HOUR + start.getMinute();
    }

    private static int endMinutes(ScheduledSession session) {
        LocalTime end = session.getTimeSlot().end;
        return end.getHour() * MINUTES_IN_HOUR + end.getMinute();
    }

    /**
     * Returns where the card of {@code session} ends, in minutes since midnight. A session shorter than
     * {@code MIN_CARD_MINUTES} gets a longer card so its details fit, but no card runs past midnight.
     */
    private static int cardEndMinutes(ScheduledSession session) {
        int start = startMinutes(session);
        int cardEnd = Math.min(MINUTES_IN_DAY, Math.max(endMinutes(session), start + MIN_CARD_MINUTES));
        assert cardEnd > start : "A card must end after it starts";
        return cardEnd;
    }

    /** Assigns each session in a day to a lane so that overlapping cards never share a lane. */
    private void placeDay(int dayIndex, List<ScheduledSession> daySessions, Function<Person, String> colourOf,
            Function<Person, String> titleOf, ToIntFunction<ScheduledSession> numberOf) {
        List<Integer> laneEnds = new ArrayList<>();
        int clusterEnd = -1;

        for (ScheduledSession session : daySessions) {
            int start = startMinutes(session);
            int end = cardEndMinutes(session);
            if (end > endMinutes(session)) {
                logger.fine("Lengthened the card of short session " + session.getSessionId() + " to fit its details");
            }
            if (clusterEnd >= 0 && start >= clusterEnd) {
                laneEnds = new ArrayList<>();
                clusterEnd = -1;
            }

            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane) > start) {
                lane++;
            }
            if (lane == laneEnds.size()) {
                laneEnds.add(end);
            } else {
                laneEnds.set(lane, end);
            }
            clusterEnd = Math.max(clusterEnd, end);
            dayLanes[dayIndex] = Math.max(dayLanes[dayIndex], laneEnds.size());

            Person student = session.getStudent();
            ScheduleSessionCard card = new ScheduleSessionCard(session, colourOf.apply(student),
                    titleOf.apply(student), numberOf.applyAsInt(session));
            placements.add(new Placement(card, dayIndex, start, end, lane));
            getChildren().add(card);
        }
    }

    private double xOf(int minutes) {
        return AXIS_WIDTH + (minutes / 60.0 - rangeStartHour) * hourWidth();
    }

    private double cardWidth(Placement placement) {
        double width = xOf(placement.end) - xOf(placement.start);
        assert width > 0 : "A card must have a positive width";
        return width;
    }

    /** Returns the lane height of each day, raised above the standard height when a card needs more room. */
    private double[] laneHeights() {
        double[] laneHeights = new double[DAYS];
        Arrays.fill(laneHeights, rowHeight());
        for (Placement placement : placements) {
            double cardHeight = Math.ceil(placement.card.prefHeight(cardWidth(placement)));
            laneHeights[placement.dayIndex] = Math.max(laneHeights[placement.dayIndex], cardHeight);
        }
        return laneHeights;
    }

    private double contentHeight() {
        double[] laneHeights = laneHeights();
        double total = HEADER_HEIGHT;
        for (int i = 0; i < DAYS; i++) {
            total += dayLanes[i] * laneHeights[i];
        }
        return total;
    }

    @Override
    protected double computePrefWidth(double height) {
        return AXIS_WIDTH + (rangeEndHour - rangeStartHour) * hourWidth();
    }

    @Override
    protected double computePrefHeight(double width) {
        return contentHeight();
    }

    @Override
    protected double computeMinHeight(double width) {
        return contentHeight();
    }

    @Override
    protected void layoutChildren() {
        double[] laneHeights = laneHeights();
        double hourWidth = hourWidth();
        double right = AXIS_WIDTH + (rangeEndHour - rangeStartHour) * hourWidth;
        double bottom = contentHeight();

        for (int i = 0; i < hourLines.size(); i++) {
            double x = AXIS_WIDTH + i * hourWidth;
            Line line = hourLines.get(i);
            line.setStartX(x);
            line.setEndX(x);
            line.setStartY(0);
            line.setEndY(bottom);
            hourLabels.get(i).resizeRelocate(x + 3, offsetY, hourWidth - 3, HEADER_HEIGHT);
        }

        double[] rowTops = new double[DAYS];
        double y = HEADER_HEIGHT;
        for (int i = 0; i < DAYS; i++) {
            rowTops[i] = y;
            double height = dayLanes[i] * laneHeights[i];
            dayLabels.get(i).resizeRelocate(offsetX, y, AXIS_WIDTH, height);
            Line line = dayLines.get(i);
            line.setStartX(0);
            line.setEndX(right);
            line.setStartY(y + height);
            line.setEndY(y + height);
            y += height;
        }

        for (Placement placement : placements) {
            double left = xOf(placement.start);
            double width = cardWidth(placement);
            double laneHeight = laneHeights[placement.dayIndex];
            double top = rowTops[placement.dayIndex] + placement.lane * laneHeight;
            placement.card.resizeRelocate(left, top, width, laneHeight);
            placement.card.setClip(new Rectangle(width, laneHeight));
        }
        corner.resizeRelocate(offsetX, offsetY, AXIS_WIDTH, HEADER_HEIGHT);
    }

    /** A card together with its position data, in minutes since midnight. */
    private static class Placement {
        private final Region card;
        private final int dayIndex;
        private final int start;
        private final int end;
        private final int lane;

        Placement(Region card, int dayIndex, int start, int end, int lane) {
            this.card = card;
            this.dayIndex = dayIndex;
            this.start = start;
            this.end = end;
            this.lane = lane;
        }
    }
}
