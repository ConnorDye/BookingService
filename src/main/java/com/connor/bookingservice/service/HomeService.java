package com.connor.bookingservice.service;

import com.connor.bookingservice.dto.ReservationResponse;
import com.connor.bookingservice.model.Home;
import com.connor.bookingservice.repository.HomeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    HomeRepository homeRepository;

    @Autowired
    public HomeService(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
    }

    public Home getHome(Long homeIdentifier) {
        return homeRepository.findById(homeIdentifier).orElseThrow(() -> new NotFoundException("Home not found for homeIdentifier " + homeIdentifier));
    }

    public Home createHome(Home home) {
        return homeRepository.save(home);
    }
}
