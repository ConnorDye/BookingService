package com.connor.bookingservice.controller;


import com.connor.bookingservice.dto.HomeResponse;
import com.connor.bookingservice.dto.ReservationResponse;
import com.connor.bookingservice.model.Home;
import com.connor.bookingservice.model.Reservation;
import com.connor.bookingservice.service.HomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/homes/v1")
public class HomeController {
    @Autowired
    HomeService homesService;

    public HomeController(HomeService homesService) {
        this.homesService = homesService;
    }

    @GetMapping("/{id}")
    public HomeResponse getHome(@PathVariable Long id) {
        Home home = homesService.getHome(id);
        return HomeResponse.from(home);

    }

    @PostMapping
    public ResponseEntity<HomeResponse> createHome(@RequestBody Home home) {
        Home saved = homesService.createHome(home);
        return responseEntityFromHome(saved);
    }

    private ResponseEntity<HomeResponse> responseEntityFromHome(Home saved) {
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/homes/v1/reservations/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(HomeResponse.from(saved));
    }


}
