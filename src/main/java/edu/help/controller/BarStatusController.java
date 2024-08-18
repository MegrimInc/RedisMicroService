package edu.help.controller;

import edu.help.service.BarStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bars")
public class BarStatusController {

    private final BarStatusService barStatusService;

    public BarStatusController(BarStatusService barStatusService) {
        this.barStatusService = barStatusService;
    }

    @PostMapping("/{barId}/open")
    public ResponseEntity<String> setBarOpenStatus(@PathVariable int barId, @RequestParam boolean open) {
        Boolean currentStatus = barStatusService.getBarOpenStatus(barId);
        if (currentStatus != null && currentStatus == open) {
            return ResponseEntity.badRequest().body("Request failed. Bar is already " + (open ? "open" : "closed") + ".");
        }

        barStatusService.setBarOpenStatus(barId, open);
        return ResponseEntity.ok("Request succeeded. Bar set to " + (open ? "open" : "closed") + ".");
    }

    @PostMapping("/{barId}/happyHour")
    public ResponseEntity<String> setBarHappyHourStatus(@PathVariable int barId, @RequestParam boolean happyHour) {
        Boolean currentStatus = barStatusService.getBarHappyHourStatus(barId);
        if (currentStatus != null && currentStatus == happyHour) {
            return ResponseEntity.badRequest().body("Request failed. Happy hour is already " + (happyHour ? "active" : "inactive") + ".");
        }

        barStatusService.setBarHappyHourStatus(barId, happyHour);
        return ResponseEntity.ok("Request succeeded. Happy hour set to " + (happyHour ? "active" : "inactive") + ".");
    }

    @GetMapping("/{barId}/status")
    public ResponseEntity<String> getBarStatus(@PathVariable int barId) {
        Boolean isOpen = barStatusService.getBarOpenStatus(barId);
        Boolean isHappyHour = barStatusService.getBarHappyHourStatus(barId);

        if (isOpen == null || isHappyHour == null) {
            return ResponseEntity.badRequest().body("Bar status not found.");
        }

        return ResponseEntity.ok("Bar is currently " + (isOpen ? "open" : "closed") + " and happy hour is " + (isHappyHour ? "active" : "inactive") + ".");
    }
}
