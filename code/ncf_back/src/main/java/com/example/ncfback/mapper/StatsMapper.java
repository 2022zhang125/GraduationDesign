package com.example.ncfback.mapper;

public interface StatsMapper {
    long countUsers();

    long countItems();

    long countInteractions();

    long countTrainingSamples();
}
