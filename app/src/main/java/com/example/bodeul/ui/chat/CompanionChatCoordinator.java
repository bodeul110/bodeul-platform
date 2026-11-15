package com.example.bodeul.ui.chat;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CompanionChatCoordinator {
    private final Context context;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("a hh:mm", Locale.KOREA);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd E", Locale.KOREA);

    public CompanionChatCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    public CompanionChatScreenModel createForBooking(
            User currentUser,
            AppointmentRequestDetail detail,
            boolean isFirebaseBacked
    ) {
        return createScreenModel(
                currentUser,
                detail.getAppointmentRequest(),
                detail.getPatient(),
                detail.getGuardian(),
                detail.getManager(),
                detail.getSession(),
                isFirebaseBacked
        );
    }

    public CompanionChatScreenModel createForManager(
            User currentUser,
            ManagerDashboard dashboard,
            boolean isFirebaseBacked
    ) {
        return createScreenModel(
                currentUser,
                dashboard.getAppointmentRequest(),
                dashboard.getPatient(),
                dashboard.getGuardian(),
                dashboard.getManager(),
                dashboard.getSession(),
                isFirebaseBacked
        );
    }

    private CompanionChatScreenModel createScreenModel(
            User currentUser,
            AppointmentRequest request,
            User patient,
            User guardian,
            User manager,
            CompanionSession session,
            boolean isFirebaseBacked
    ) {
        String patientName = patient != null && !TextUtils.isEmpty(patient.getName())
                ? patient.getName()
                : request.getPatientName();
        String managerName = manager != null && !TextUtils.isEmpty(manager.getName())
                ? manager.getName()
                : context.getString(R.string.companion_chat_role_manager);

        return new CompanionChatScreenModel(
                request.getId(),
                currentUser.getId() + ":" + request.getId(),
                currentUser.getRole() == UserRole.MANAGER,
                session.getStatus() != SessionStatus.CARE_ENDED
                        && session.getStatus() != SessionStatus.COMPLETED
                        && session.getStatus() != SessionStatus.CANCELED,
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                currentUser.getRole() == UserRole.MANAGER
                        ? namedRole(patientName, UserRole.PATIENT)
                        : namedRole(manager == null ? "" : manager.getName(), UserRole.MANAGER),
                context.getString(
                        currentUser.getRole() == UserRole.MANAGER
                                ? R.string.companion_chat_subtitle_manager
                                : R.string.companion_chat_subtitle_user
                ),
                toSessionStatusLabel(session.getStatus()),
                context.getString(R.string.companion_chat_hero_title, patientName),
                context.getString(
                        R.string.companion_chat_hero_body,
                        request.getHospitalName(),
                        request.getDepartmentName(),
                        request.getAppointmentAt(),
                        managerName
                ),
                context.getString(R.string.companion_chat_section_title),
                context.getString(R.string.companion_chat_empty_body),
                context.getString(R.string.final_chat_hint),
                context.getString(R.string.companion_chat_send),
                toMessageItems(currentUser, request, patient, guardian, manager, session)
        );
    }

    private List<CompanionChatMessageItemModel> toMessageItems(
            User currentUser,
            AppointmentRequest request,
            User patient,
            User guardian,
            User manager,
            CompanionSession session
    ) {
        List<CompanionChatMessageItemModel> items = new ArrayList<>();
        List<CompanionChatMessage> messages = session.getChatMessages();
        int lastMineIndex = findLastMineIndex(currentUser, messages);
        String previousDate = "";
        for (int index = 0; index < messages.size(); index++) {
            CompanionChatMessage message = messages.get(index);
            boolean mine = currentUser.getRole() == message.getSenderRole();
            String sentAtLabel = timeFormat.format(new Date(message.getSentAtMillis()));
            if (mine && index == lastMineIndex && hasCounterpartReadAfter(
                    currentUser,
                    request,
                    patient,
                    guardian,
                    manager,
                    session,
                    message.getSentAtMillis()
            )) {
                sentAtLabel = context.getString(
                        R.string.companion_chat_read_confirmed_format,
                        sentAtLabel,
                        context.getString(R.string.companion_chat_read_confirmed)
                );
            }
            items.add(new CompanionChatMessageItemModel(
                    dateFormat.format(new Date(message.getSentAtMillis())).equals(previousDate)
                            ? "" : dateFormat.format(new Date(message.getSentAtMillis())),
                    senderLabel(message.getSenderRole(), patient, guardian, manager),
                    message.getBody(),
                    sentAtLabel,
                    mine,
                    toAttachmentItems(message.getAttachments())
            ));
            previousDate = dateFormat.format(new Date(message.getSentAtMillis()));
        }
        return items;
    }

    private String senderLabel(UserRole role, User patient, User guardian, User manager) {
        User sender = role == UserRole.PATIENT ? patient
                : role == UserRole.GUARDIAN ? guardian : role == UserRole.MANAGER ? manager : null;
        return namedRole(sender == null ? "" : sender.getName(), role);
    }

    private String namedRole(String name, UserRole role) {
        return TextUtils.isEmpty(name) ? toRoleLabel(role)
                : context.getString(R.string.final_chat_counterpart, name, toRoleLabel(role));
    }

    private List<CompanionChatAttachmentItemModel> toAttachmentItems(
            List<CompanionChatAttachment> attachments
    ) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompanionChatAttachmentItemModel> items = new ArrayList<>();
        for (CompanionChatAttachment attachment : attachments) {
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            String fileName = TextUtils.isEmpty(attachment.getFileName())
                    ? context.getString(R.string.companion_chat_attachment_file)
                    : attachment.getFileName();
            String summary;
            if (attachment.isImageType()) {
                summary = context.getString(R.string.companion_chat_attachment_image_format, fileName);
            } else if (attachment.isPdfType()) {
                summary = context.getString(R.string.companion_chat_attachment_pdf_format, fileName);
            } else {
                summary = context.getString(R.string.companion_chat_attachment_file_format, fileName);
            }
            items.add(new CompanionChatAttachmentItemModel(
                    attachment,
                    summary,
                    context.getString(R.string.companion_chat_attachment_open)
            ));
        }
        return items;
    }

    private int findLastMineIndex(User currentUser, List<CompanionChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index).getSenderRole() == currentUser.getRole()) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasCounterpartReadAfter(
            User currentUser,
            AppointmentRequest request,
            User patient,
            User guardian,
            User manager,
            CompanionSession session,
            long sentAtMillis
    ) {
        for (UserRole counterpartRole : resolveCounterpartRoles(currentUser, request, patient, guardian, manager)) {
            if (session.getChatReadAtMillis(counterpartRole) >= sentAtMillis) {
                return true;
            }
        }
        return false;
    }

    private List<UserRole> resolveCounterpartRoles(
            User currentUser,
            AppointmentRequest request,
            User patient,
            User guardian,
            User manager
    ) {
        List<UserRole> roles = new ArrayList<>();
        if (currentUser.getRole() != UserRole.MANAGER && manager != null) {
            roles.add(UserRole.MANAGER);
        }
        if (currentUser.getRole() != UserRole.PATIENT
                && patient != null
                && !TextUtils.isEmpty(request.getPatientUserId())) {
            roles.add(UserRole.PATIENT);
        }
        if (currentUser.getRole() != UserRole.GUARDIAN
                && guardian != null
                && !TextUtils.isEmpty(request.getGuardianUserId())) {
            roles.add(UserRole.GUARDIAN);
        }
        return roles;
    }

    private String toRoleLabel(UserRole role) {
        if (role == UserRole.PATIENT) {
            return context.getString(R.string.companion_chat_role_patient);
        }
        if (role == UserRole.GUARDIAN) {
            return context.getString(R.string.companion_chat_role_guardian);
        }
        if (role == UserRole.MANAGER) {
            return context.getString(R.string.companion_chat_role_manager);
        }
        return context.getString(R.string.companion_chat_role_admin);
    }

    private String toSessionStatusLabel(SessionStatus status) {
        switch (status) {
            case READY:
                return context.getString(R.string.companion_chat_status_ready);
            case MEETING:
                return context.getString(R.string.companion_chat_status_meeting);
            case WAITING:
                return context.getString(R.string.companion_chat_status_waiting);
            case IN_TREATMENT:
                return context.getString(R.string.companion_chat_status_treatment);
            case PAYMENT:
                return context.getString(R.string.companion_chat_status_payment);
            case CARE_ENDED:
                return context.getString(R.string.companion_chat_status_care_ended);
            case COMPLETED:
                return context.getString(R.string.companion_chat_status_completed);
            case CANCELED:
            default:
                return context.getString(R.string.companion_chat_status_canceled);
        }
    }
}
