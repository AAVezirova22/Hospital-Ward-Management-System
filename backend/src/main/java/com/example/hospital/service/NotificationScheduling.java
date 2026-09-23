package com.example.hospital.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the notification sweep. Declaring it again elsewhere is harmless. */
@Configuration
@EnableScheduling
public class NotificationScheduling {}
