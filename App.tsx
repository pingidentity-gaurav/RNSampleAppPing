import * as React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import DogStorageScreen from './src/ui/DogStorageScreeen';
import HomeScreen from './src/ui/HomeScreen';
import JourneyScreen from './src/ui/JourneyScreen';

export type RootStackParamList = {
  Home: undefined;
  DogStorage: undefined;
  Journey: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: 'PingIdentity Demo', headerShown: false }}
        />
        <Stack.Screen
          name="DogStorage"
          component={DogStorageScreen}
          options={{ title: 'Dog Storage' }}
        />
        <Stack.Screen
          name="Journey"
          component={JourneyScreen}
          options={{ title: 'Journey' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
