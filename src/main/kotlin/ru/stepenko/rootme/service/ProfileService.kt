package ru.stepenko.rootme.service

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import ru.stepenko.rootme.model.entity.Profile

@Service
class ProfileService(private val profileRepository: JpaRepository<Profile, String>) {
    fun create(profile: Profile): Profile = profileRepository.save(profile)

    fun update(profile: Profile): Profile {
//        TODO добавить проверку на существование профиля с таким id
        return profileRepository.save(profile)
    }

    fun getProfile(profileId: String): Profile = profileRepository.getReferenceById(profileId)

    fun delete(profileId: String) = profileRepository.deleteById(profileId)

}
