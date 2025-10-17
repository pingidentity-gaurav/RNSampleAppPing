import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  ActivityIndicator,
  ScrollView,
  TextInput,
  TouchableOpacity,
  Alert,
} from 'react-native';
import { useJourney } from '../hooks/useJourney';
import { JourneyUserSession } from '../specs/NativePingStorage';
import { colors } from '../styles/colors';
import { commonStyles } from '../styles/common';

const journeyConfig = {
  serverUrl: 'https://openam-sdks.forgeblocks.com/am',
  realm: 'alpha',
  cookie: '5421aeddf91aa20',
  clientId: 'sdkPublicClient',
  discoveryEndpoint:
    'https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration',
  redirectUri: 'org.forgerock.demo://oauth2redirect',
  scopes: ['openid', 'email', 'profile', 'address'],
};

export default function JourneyScreen() {
  const [node, { start, next, resume, user, logout, loading, error }] =
    useJourney(journeyConfig);
  const [inputs, setInputs] = useState<Record<string, string>>({});
  const [session, setSession] = useState<any>(null);
  const [resumeUrl, setResumeUrl] = useState('');
  const [givenName, setGivenName] = useState<string>();

  // Fetch user info after success
  useEffect(() => {
    const getUserDetails = async () => {
      try {
        const journeyUser = (await user()) as JourneyUserSession | null;
        if (!journeyUser) return;
        const gn = journeyUser.userInfo?.given_name as string | undefined;
        if (gn) setGivenName(gn);
      } catch (err) {
        console.error('❌ Error fetching user details:', err);
      }
    };
    if (node?.type === 'SuccessNode') getUserDetails();
  }, [node, user]);

  const onStart = async () => await start('Login');

  const onSubmit = async () => {
    if (!node?.callbacks) return;
    const callbacks = node.callbacks.map((cb: any) => ({
      type: cb.type,
      value: inputs[cb.type] || '',
    }));
    await next({ callbacks });
  };

  const onGetUser = async () => {
    const journeyUser = (await user()) as JourneyUserSession;
    setSession(journeyUser?.accessToken);
  };

  const onLogout = async () => {
    try {
      await logout();
      setSession(null);
      setInputs({});
      setResumeUrl('');
      Alert.alert('Logged out');
    } catch (err: any) {
      Alert.alert('⚠️ Logout failed', err.message);
    }
  };

  const renderCallbacks = () =>
    node?.callbacks?.map((cb: any, index: number) => {
      const key = `${cb.type}-${index}`;
      const label =
        cb.prompt ||
        (cb.type === 'NameCallback'
          ? 'Username'
          : cb.type === 'PasswordCallback'
          ? 'Password'
          : cb.type);
      const secure = cb.type === 'PasswordCallback';
      return (
        <View key={key} style={{ marginBottom: 14 }}>
          <Text style={commonStyles.inputLabel}>{label}</Text>
          <TextInput
            style={commonStyles.input}
            secureTextEntry={secure}
            autoCapitalize="none"
            value={inputs[cb.type] || ''}
            onChangeText={text =>
              setInputs(prev => ({ ...prev, [cb.type]: text }))
            }
            placeholder={label}
            placeholderTextColor={colors.gray}
          />
        </View>
      );
    });

  return (
    <ScrollView contentContainerStyle={commonStyles.container}>
      <View style={commonStyles.card}>
        {loading && <ActivityIndicator size="large" color={colors.primary} />}

        {!node && (
          <TouchableOpacity
            style={[commonStyles.buttonPrimary, loading && { opacity: 0.5 }]}
            onPress={onStart}
            disabled={loading}
          >
            <Text style={commonStyles.buttonText}>Start Journey</Text>
          </TouchableOpacity>
        )}

        {node?.type === 'ContinueNode' && (
          <>
            {renderCallbacks()}
            <TouchableOpacity
              style={commonStyles.buttonPrimary}
              onPress={onSubmit}
              disabled={loading}
            >
              <Text style={commonStyles.buttonText}>Continue</Text>
            </TouchableOpacity>
          </>
        )}

        {node?.type === 'SuccessNode' && (
          <>
            <Text style={commonStyles.textSuccess}>
              Welcome {givenName ?? 'User'}!
            </Text>
            <TouchableOpacity
              style={commonStyles.buttonSecondary}
              onPress={onGetUser}
            >
              <Text style={commonStyles.buttonTextSecondary}>
                Get User Info
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={commonStyles.buttonDanger}
              onPress={onLogout}
            >
              <Text style={commonStyles.buttonText}>Logout</Text>
            </TouchableOpacity>
          </>
        )}

        {node?.type === 'ErrorNode' && (
          <Text style={commonStyles.textError}>
            ⚠️ {node.message || 'Error occurred'}
          </Text>
        )}

        {error && (
          <Text style={commonStyles.textError}>
            {error.message ?? String(error)}
          </Text>
        )}
      </View>

      {session && (
        <View style={commonStyles.codeBox}>
          <Text style={commonStyles.codeTitle}>Access Token</Text>
          <Text style={commonStyles.codeText}>
            {JSON.stringify(session, null, 2)}
          </Text>
        </View>
      )}

      <View style={commonStyles.card}>
        <Text style={commonStyles.codeTitle}>Resume Journey</Text>
        <TextInput
          style={commonStyles.input}
          placeholder="Paste resume URL here"
          value={resumeUrl}
          onChangeText={setResumeUrl}
          autoCapitalize="none"
        />
        <TouchableOpacity
          style={[commonStyles.buttonPrimary, { backgroundColor: colors.blue }]}
          onPress={() => resume(resumeUrl.trim())}
        >
          <Text style={commonStyles.buttonText}>Resume Journey</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}
