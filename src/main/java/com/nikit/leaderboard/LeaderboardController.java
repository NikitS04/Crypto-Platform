package com.nikit.leaderboard;

import com.nikit.modal.User;
import com.nikit.repository.UserRepository;
import com.nikit.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class LeaderboardController {

    private static final int MAX_LIMIT = 100;

    public record Row(long rank, String name, double returnPct, boolean you) {
    }

    public record Leaderboard(List<Row> entries, Long yourRank) {
    }

    private final LeaderboardStore store;
    private final UserRepository userRepository;
    private final UserService userService;

    public LeaderboardController(LeaderboardStore store, UserRepository userRepository, UserService userService) {
        this.store = store;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping("/api/leaderboard")
    public ResponseEntity<Leaderboard> leaderboard(@RequestHeader("Authorization") String jwt,
                                                   @RequestParam(defaultValue = "20") int limit) throws Exception {
        User me = userService.findUserProfileByJwt(jwt);
        List<LeaderboardStore.Entry> top = store.top(Math.max(1, Math.min(limit, MAX_LIMIT)));

        Map<Long, User> users = userRepository.findAllById(top.stream().map(LeaderboardStore.Entry::userId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            LeaderboardStore.Entry e = top.get(i);
            rows.add(new Row(i + 1, displayName(users.get(e.userId())),
                    Math.round(e.returnPct() * 100.0) / 100.0, e.userId() == me.getId()));
        }
        return ResponseEntity.ok(new Leaderboard(rows, store.rank(me.getId()).orElse(null)));
    }

    /** "Ada Lovelace" -> "Ada L." so the board doesn't expose full names or emails. */
    static String displayName(User user) {
        if (user == null || user.getFullName() == null || user.getFullName().isBlank()) {
            return "Anonymous";
        }
        String[] parts = user.getFullName().trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
