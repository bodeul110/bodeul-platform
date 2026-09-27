package com.example.bodeul.debug;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/** debug 미리보기에서만 사용할 로컬 인증과 가이드 저장소를 묶는다. */
final class ManagerGuidePreviewDependencies {
    final AuthRepository authRepository;
    final ManagerRepository managerRepository;

    private ManagerGuidePreviewDependencies(
            AuthRepository authRepository,
            ManagerRepository managerRepository
    ) {
        this.authRepository = authRepository;
        this.managerRepository = managerRepository;
    }

    static ManagerGuidePreviewDependencies create(Context context, String stepCode) {
        ManagerGuidePreviewRepository managerRepository =
                new ManagerGuidePreviewRepository(stepCode);
        MockAuthRepository authRepository =
                new MockAuthRepository(managerRepository.dataRepository());
        authRepository.signIn(
                context.getString(R.string.demo_account_manager_email),
                context.getString(R.string.demo_account_password),
                UserRole.MANAGER,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        // 로컬 매니저를 캐시하면 미리보기 준비가 끝난다.
                    }

                    @Override
                    public void onError(String message) {
                        throw new IllegalStateException(message);
                    }
                });
        return new ManagerGuidePreviewDependencies(authRepository, managerRepository);
    }
}
