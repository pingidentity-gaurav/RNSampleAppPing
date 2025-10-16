import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
//import NativePingBrowser from '../specs/NativePingBrowser';
import NativePingStorage from '../specs/NativePingStorage';

type RootStackParamList = {
  Home: undefined;
  DogStorage: undefined;
  PingBrowser: undefined;
};

type HomeScreenNavProp = NativeStackNavigationProp<RootStackParamList, 'Home'>;

type Props = {
  navigation: HomeScreenNavProp;
};

export default function HomeScreen({ navigation }: Props) {
  const menuItems = [
    { title: '🐶 Go to Dog Storage', screen: 'DogStorage' },
    { title: '🌐 Launch Ping Browser', screen: 'PingBrowser' },
  ];

  async function openBrowser() {
    // const redirect = await NativePingBrowser.launch(
    //   'https://httpbin.org/redirect-to?url=myapp://callback?code=12345', // Automatic callback test url
    //   'myapp://callback',
    // );

    // console.log('Redirect received:', redirect);
  }


  return (
    <View style={styles.container}>
      {menuItems.map((item, index) => (
        <TouchableOpacity
          key={index}
          style={styles.row}
          onPress={() => {
            if (item.screen === 'DogStorage') navigation.navigate(item.screen);
            else openBrowser();
          }}
        >
          <Text style={styles.rowText}>{item.title}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f9f9f9',
    paddingTop: 40,
  },
  row: {
    backgroundColor: '#fff',
    paddingVertical: 20,
    paddingHorizontal: 16,
    marginVertical: 8,
    marginHorizontal: 20,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 6,
    elevation: 3,
  },
  rowText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
});
