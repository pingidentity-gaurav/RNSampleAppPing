import * as React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import DogStorageScreen from './src/ui/DogStorageScreeen';
import HomeScreen from './src/ui/HomeScreen';

export type RootStackParamList = {
  Home: undefined;
  DogStorage: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: 'PingIdentity Demo' }}
        />
        <Stack.Screen
          name="DogStorage"
          component={DogStorageScreen}
          options={{ title: 'Dog Storage' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
