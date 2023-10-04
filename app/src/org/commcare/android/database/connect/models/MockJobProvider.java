package org.commcare.android.database.connect.models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Temporary class for creating mock ConnectJobs
 *
 * @author dviggiano
 */
public class MockJobProvider {
    private static final List<ConnectJobRecord> mockAvailableJobs;
    private static final List<ConnectJobRecord> mockClaimedJobs;

    static {
        mockAvailableJobs = new ArrayList();
        mockAvailableJobs.add(
                new ConnectJobRecord(1, "Infant Vaccine Check",
                        "You will conduct 100 home visits to assess if children below 2 years are up to date with their vaccine shots.",
                        ConnectJobRecord.STATUS_AVAILABLE_NEW,
                        0, 100, 5, 5, 100,
                        new Date("11/1/2023"),
                        null,
//                        new ConnectJobLearningModule[]{
//                                new ConnectJobLearningModule("How to check the vaccine booklet of a child in the household", 2, new Date("8/10/2023")),
//                                new ConnectJobLearningModule("How to help the caregiver in the household get the next vaccine shot for their child", 2, null),
//                        },
                        new ArrayList<>()
                ));
        mockAvailableJobs.add(new ConnectJobRecord(2, "Vitamin A Delivery",
                "You will deliver Vitamin A supplements to 50 homes.",
                ConnectJobRecord.STATUS_AVAILABLE,
                0, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{
//                        new ConnectJobLearningModule("Sample learning module", 2, null),
//                },
                new ArrayList<>()
        ));
        mockAvailableJobs.add(new ConnectJobRecord(3, "Training Complete Example",
                "This mock shows when training is complete for a job.",
                ConnectJobRecord.STATUS_AVAILABLE,
                0, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{
//                        new ConnectJobLearningModule("Sample learning module", 2, new Date("8/10/2023")),
//                },
                new ArrayList<>()
        ));

        mockClaimedJobs = new ArrayList<>();

        List<ConnectJobDeliveryRecord> deliveries = new ArrayList<>();

        mockClaimedJobs.add(new ConnectJobRecord(1, "Mental Health Visits",
                "",
                ConnectJobRecord.STATUS_DELIVERING,
                60, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{},
                deliveries));
        mockClaimedJobs.add(new ConnectJobRecord(2, "TestA",
                "",
                ConnectJobRecord.STATUS_DELIVERING,
                0, 100, 5, 5, 100,
                new Date("11/1/2023"),
                null,
//                new ConnectJobLearningModule[]{},
                new ArrayList<>()));
        mockClaimedJobs.add(new ConnectJobRecord(3, "Infant Health Check",
                "",
                ConnectJobRecord.STATUS_DELIVERING,
                100, 100, 5, 5, 100,
                new Date("10/1/2023"),
                new Date("4/7/2023"),
//                new ConnectJobLearningModule[]{},
                new ArrayList<>()));
    }

    public static List<ConnectJobRecord> getAvailableJobs() {
        return mockAvailableJobs;
    }

    public static List<ConnectJobRecord> getTrainingJobs() {
        return mockAvailableJobs;
    }

    public static List<ConnectJobRecord> getClaimedJobs() {
        return mockClaimedJobs;
    }
}
