
package com.webvideoplayer.player.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PersonProfileDTO {

    private String personId;
    private String name;
    private String biography;
    private String profileImageUrl;
    private String heroImageUrl;
}
