package dev.vlearning.lending;

import java.time.LocalDate;
import java.util.List;

/**
 * The "member activity" dashboard — the read shape this whole lesson revolves
 * around. Note how far it is from the write-side tables: counts, distinct
 * counts, a ranking (favorite author) and a nested list, all for one screen.
 *
 * <p>Contract details both implementations must honor:
 * <ul>
 *   <li>{@code favoriteAuthor}: the author with the most loans (repeat loans of
 *       the same book count); ties break alphabetically. {@code null} when the
 *       member has never borrowed.</li>
 *   <li>{@code lastActivityOn}: the latest borrow or return date; {@code null}
 *       when the member has never borrowed.</li>
 *   <li>{@code currentLoans}: open loans sorted by due date, then loan id.
 *       {@code overdue} means the due date lies strictly before today.</li>
 *   <li>A return on the due date itself is not late.</li>
 * </ul>
 */
public record MemberActivity(
        long memberId,
        String name,
        String email,
        int totalLoans,
        int openLoans,
        int returnedLoans,
        int lateReturns,
        int distinctBooks,
        int distinctAuthors,
        String favoriteAuthor,
        LocalDate lastActivityOn,
        List<OpenLoan> currentLoans) {

    public record OpenLoan(long loanId, String title, LocalDate dueOn, boolean overdue) {
    }
}
