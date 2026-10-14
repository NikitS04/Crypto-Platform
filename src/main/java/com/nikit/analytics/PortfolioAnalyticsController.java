package com.nikit.analytics;

import com.nikit.modal.User;
import com.nikit.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PortfolioAnalyticsController {

    private final UserService userService;
    private final PortfolioAnalyticsService analyticsService;

    public PortfolioAnalyticsController(UserService userService, PortfolioAnalyticsService analyticsService) {
        this.userService = userService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/portfolio/analytics")
    public ResponseEntity<PortfolioAnalytics> analytics(@RequestHeader("Authorization") String jwt) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        return ResponseEntity.ok(analyticsService.analyse(user.getId()));
    }
}
