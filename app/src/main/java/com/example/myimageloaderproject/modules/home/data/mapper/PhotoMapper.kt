package com.example.myimageloaderproject.modules.home.data.mapper

import com.example.myimageloaderproject.modules.home.data.model.UnsplashLinksDTO
import com.example.myimageloaderproject.modules.home.data.model.UnsplashPhotoDTO
import com.example.myimageloaderproject.modules.home.data.model.UnsplashUrlsDTO
import com.example.myimageloaderproject.modules.home.data.model.UnsplashUserDTO
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashLinks
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashUrls
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashUser

class PhotoMapper {

    fun toDomain(dto: UnsplashPhotoDTO): UnsplashPhoto {
        return UnsplashPhoto(
            id = dto.id,
            created_at = dto.created_at,
            width = dto.width,
            height = dto.height,
            color = dto.color,
            likes = dto.likes,
            description = dto.description,
            alt_description = null,
            urls = toDomain(dto.urls),
            links = toDomain(dto.links),
            user = toDomain(dto.user)
        )
    }

    fun toDomainList(dtos: List<UnsplashPhotoDTO>): List<UnsplashPhoto> {
        return dtos.map { toDomain(it) }
    }

    private fun toDomain(dto: UnsplashUrlsDTO): UnsplashUrls {
        return UnsplashUrls(
            thumb = dto.thumb,
            small = dto.small,
            medium = dto.medium,
            regular = dto.regular,
            large = dto.large,
            full = dto.full,
            raw = dto.raw
        )
    }

    private fun toDomain(dto: UnsplashLinksDTO): UnsplashLinks {
        return UnsplashLinks(
            self = dto.self,
            html = dto.html,
            photos = dto.photos,
            likes = dto.likes,
            portfolio = dto.portfolio,
            download = dto.download,
            download_location = dto.download_location
        )
    }

    private fun toDomain(dto: UnsplashUserDTO): UnsplashUser {
        return UnsplashUser(
            id = dto.id,
            username = dto.username,
            name = dto.name,
            portfolio_url = dto.portfolio_url,
            bio = dto.bio,
            location = dto.location,
            total_likes = dto.total_likes,
            total_photos = dto.total_photos,
            total_collection = dto.total_collection,
            profile_image = toDomain(dto.profile_image),
            links = toDomain(dto.links)
        )
    }
}
