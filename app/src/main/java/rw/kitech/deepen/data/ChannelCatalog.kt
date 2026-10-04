package rw.kitech.deepen.data

import rw.kitech.deepen.model.ChannelSource

object ChannelCatalog {
    const val DEFAULT_CHANNEL_ID = "paul-gitwaza"

    val featuredChannels = listOf(
        ChannelSource(
            id = DEFAULT_CHANNEL_ID,
            displayName = "Apostle Dr. Paul M. Gitwaza",
            sourceName = "Dr Paul Gitwaza",
            youtubeChannelId = "UCXGkBYxkiiqdlyvlwOJvHjw",
            youtubeHandle = "drpaulmgitwaza",
            lookupQuery = "Dr Paul Gitwaza",
            featured = true,
            featuredOrder = 1,
            isDefault = true,
        ),
        ChannelSource(
            id = "yoshua-masasu",
            displayName = "Apostle Yoshua N. Masasu",
            sourceName = "BCN TV",
            youtubeChannelId = "UCF22W7Vkp6bjqQiPOm84Viw",
            youtubeHandle = "bcntvofficial",
            lookupQuery = "BCN TV Apostle Yoshua Masasu",
            featured = true,
            featuredOrder = 2,
        ),
        ChannelSource(
            id = "alice-mignonne-kabera",
            displayName = "Apostle Alice Mignonne Kabera",
            sourceName = "Women Foundation Ministries",
            youtubeChannelId = "UCcujMiWT_Asg6IcC7_-rYVQ",
            lookupQuery = "Apostle Mignonne Alice Kabera",
            featured = true,
            featuredOrder = 3,
        ),
        ChannelSource(
            id = "charles-mugisha",
            displayName = "Rev. Dr. Charles Mugisha",
            sourceName = "New Life Rwanda",
            youtubeChannelId = "UCqGo37aKTm1ooG2t1xEaiiQ",
            youtubeHandle = "newliferwanda",
            lookupQuery = "New Life Rwanda Charles Mugisha",
            featured = true,
            featuredOrder = 4,
        ),
        ChannelSource(
            id = "fidele-masengo",
            displayName = "Bishop Prof. Fidèle Masengo",
            sourceName = "CITYLIGHT FOURSQUARE CHURCH",
            youtubeChannelId = "UC5xuygrf-HBsgG9UJAtxEwQ",
            youtubeHandle = "citylightfoursquaretv",
            lookupQuery = "CityLight Foursquare Church Rwanda Fidele Masengo",
            featured = true,
            featuredOrder = 5,
        ),
    )
}
