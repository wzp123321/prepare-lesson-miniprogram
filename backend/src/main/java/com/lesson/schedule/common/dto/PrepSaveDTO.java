package com.lesson.schedule.common.dto;

import java.util.List;
import lombok.Data;

/** 保存某节课的备课安排（整体覆盖）。入参：lessonId + kpIds + paperIds + remark。 */
@Data
public class PrepSaveDTO {

    private Long lessonId;
    private List<Long> kpIds;
    private List<Long> paperIds;
    private String remark;
}
