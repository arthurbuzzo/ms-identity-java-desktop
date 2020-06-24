// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IntegratedWindowsAuthenticationParameters;
import com.microsoft.aad.msal4j.MsalException;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

public class IntegratedWindowsAuthFlow {

    private static String authority;
    private static Set<String> scope;
    private static String clientId;
    private static String username;
    private static String accounts;

    public static void main(String args[]) throws Exception {

        setUpSampleData();

        TokenCacheAspect tokenCacheAspect = new TokenCacheAspect(accounts);

        PublicClientApplication pca = PublicClientApplication.builder(clientId)
                .authority(authority)
                .setTokenCacheAccessAspect(tokenCacheAspect)
                .build();

        Set<IAccount> accountsInCache = pca.getAccounts().join();
        IAccount account = getAccountByUsername(accountsInCache, username);

        //Attempt to acquire token when user's account is not in the application's token cache
        IAuthenticationResult result = acquireTokenIntegratedWindowsAuth(pca, scope, account, username);
        System.out.println("Account username: " + result.account().username());
        System.out.println("Access token:     " + result.accessToken());
        System.out.println("Id token:         " + result.idToken());
        System.out.println();

        accountsInCache = pca.getAccounts().join();
        account = getAccountByUsername(accountsInCache, username);

        //Attempt to acquire token again, now that the user's account and a token are in the application's token cache
        result = acquireTokenIntegratedWindowsAuth(pca, scope, account, username);
        System.out.println("Account username: " + result.account().username());
        System.out.println("Access token:     " + result.accessToken());
        System.out.println("Id token:         " + result.idToken());
    }

    private static IAuthenticationResult acquireTokenIntegratedWindowsAuth( PublicClientApplication pca,
                                                                            Set<String> scope,
                                                                            IAccount account,
                                                                            String username) throws Exception {

        IAuthenticationResult result;
        try {
            SilentParameters silentParameters =
                    SilentParameters
                            .builder(scope)
                            .account(account)
                            .build();
            // Try to acquire token silently. This will fail on the first acquireTokenIntegratedWindowsAuth() call
            // because the token cache does not have any data for the user you are trying to acquire a token for
            result = pca.acquireTokenSilently(silentParameters).join();
            System.out.println("==acquireTokenSilently call succeeded");
        } catch (Exception ex) {
            if (ex.getCause() instanceof MsalException) {
                System.out.println("==acquireTokenSilently call failed: " + ex.getCause());
                IntegratedWindowsAuthenticationParameters parameters =
                        IntegratedWindowsAuthenticationParameters
                                .builder(scope, username)
                                .build();

                // Try to acquire a token using Integrated Windows Authentication (IWA). You will need to generate a Kerberos ticket.
                // If successful, you should see the token and account information printed out to console
                result = pca.acquireToken(parameters).join();
                System.out.println("==Integrated Windows Authentication flow succeeded");
            } else {
                // Handle other exceptions accordingly
                throw ex;
            }
        }
        return result;
    }

    /**
     * Helper function to return an account from a given set of accounts based on the given username,
     * or return null if no accounts in the set match
     */
    private static IAccount getAccountByUsername(Set<IAccount> accounts, String username) {
        for (IAccount account : accounts) {
            if (account.username().equals(username)) {
                return account;
            }
        }
        return null;
    }

    /**
     * Helper function unique to this sample setting. In a real application these wouldn't be so hardcoded, for example
     * values such as username would come from the user, and different users may require different scopes
     */
    private static void setUpSampleData() throws IOException {
        // Load properties file and set properties used throughout the sample
        Properties properties = new Properties();
        properties.load(new FileInputStream(Thread.currentThread().getContextClassLoader().getResource("").getPath() + "application.properties"));
        authority = properties.getProperty("AUTHORITY");
        scope = Collections.singleton(properties.getProperty("SCOPE"));
        clientId = properties.getProperty("CLIENT_ID");
        username = properties.getProperty("USER_NAME");
        // Junk account added to initial cache when the application is created, to better demonstrate searching the cache.
        // In a real application, accounts would be automatically added to the cache when acquiring tokens, as can be
        // seen in the set returned from pca.getAccounts() after the first run of acquireTokenIntegratedWindowsAuth()
        accounts = "{\"Account\": "
                + "{\"uid.utid-login.windows.net-contoso\": "
                + "{\"username\": \"John Doe\","
                + "\"local_account_id\": \"object1234\","
                + "\"realm\": \"contoso\","
                + "\"environment\": \"login.windows.net\","
                + "\"home_account_id\": \"uid.utid\","
                + "\"authority_type\": \"MSSTS\"}}}";
    }
}