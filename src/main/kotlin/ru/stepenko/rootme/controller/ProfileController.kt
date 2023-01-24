package ru.stepenko.rootme.controller

import org.springframework.web.bind.annotation.RestController
import ru.stepenko.rootme.model.entity.Profile
import ru.stepenko.rootme.service.ProfileService

@RestController
class ProfileController(private val profileService: ProfileService) {

    fun create(profile: Profile): Profile = profileService.create(profile)

    fun update(profile: Profile): Profile = profileService.update(profile)

    fun getProfile(profileId: String): Profile = profileService.getProfile(profileId)

    fun delete(ProfileId: String) = profileService.delete(ProfileId)
}