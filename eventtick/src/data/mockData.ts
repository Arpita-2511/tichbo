import type {
  Content, Venue, Show, Seat, SeatRow, SeatSection, Booking, User, Plan,
  RateLimitPolicy, AdminStats
} from '../types';

// ─────────────────────────────────────────────────────────────────────────────
// VENUES
// ─────────────────────────────────────────────────────────────────────────────

export const venues: Venue[] = [
  {
    id: 'v1',
    name: 'Wankhede Stadium',
    address: 'D Rd, Churchgate',
    city: 'Mumbai',
    state: 'Maharashtra',
    pincode: '400020',
    amenities: ['Parking', 'Food Court', 'VIP Lounge', 'ATM', 'First Aid'],
    totalCapacity: 33000,
  },
  {
    id: 'v2',
    name: 'Jawaharlal Nehru Stadium',
    address: 'Lodhi Road',
    city: 'New Delhi',
    state: 'Delhi',
    pincode: '110003',
    amenities: ['Parking', 'Food Stalls', 'VIP Box', 'ATM'],
    totalCapacity: 75000,
  },
  {
    id: 'v3',
    name: 'M. Chinnaswamy Stadium',
    address: 'MG Road',
    city: 'Bangalore',
    state: 'Karnataka',
    pincode: '560001',
    amenities: ['Parking', 'Food Court', 'Club House'],
    totalCapacity: 40000,
  },
  {
    id: 'v4',
    name: 'INOX Megaplex, BKC',
    address: 'Bandra Kurla Complex',
    city: 'Mumbai',
    state: 'Maharashtra',
    pincode: '400051',
    amenities: ['IMAX', 'Parking', 'Cafeteria', '4DX'],
    totalCapacity: 1200,
  },
  {
    id: 'v5',
    name: 'PVR ICON, Aerocity',
    address: 'T3 Hospitality District, Aerocity',
    city: 'New Delhi',
    state: 'Delhi',
    pincode: '110037',
    amenities: ['IMAX', 'Parking', 'Dolby Atmos', 'Cafeteria'],
    totalCapacity: 800,
  },
  {
    id: 'v6',
    name: 'Palace Grounds',
    address: 'Bellary Road',
    city: 'Bangalore',
    state: 'Karnataka',
    pincode: '560080',
    amenities: ['Outdoor Arena', 'Food Court', 'Parking', 'VIP Zone'],
    totalCapacity: 80000,
  },
  {
    id: 'v7',
    name: 'Hiranandani Meadows',
    address: 'Thane West',
    city: 'Mumbai',
    state: 'Maharashtra',
    pincode: '400610',
    amenities: ['Open Air', 'Parking', 'Food Stalls'],
    totalCapacity: 20000,
  },
  {
    id: 'v8',
    name: 'Nehru Centre Auditorium',
    address: 'Dr. Annie Besant Road, Worli',
    city: 'Mumbai',
    state: 'Maharashtra',
    pincode: '400018',
    amenities: ['AC', 'Parking', 'Cafeteria'],
    totalCapacity: 1000,
  },
  {
    id: 'v9',
    name: 'Siri Fort Auditorium',
    address: 'August Kranti Marg',
    city: 'New Delhi',
    state: 'Delhi',
    pincode: '110049',
    amenities: ['AC', 'Cafeteria', 'Parking'],
    totalCapacity: 700,
  },
  {
    id: 'v10',
    name: 'DY Patil Stadium',
    address: 'Nerul, Navi Mumbai',
    city: 'Mumbai',
    state: 'Maharashtra',
    pincode: '400706',
    amenities: ['VIP Lounge', 'Food Court', 'Parking', 'ATM'],
    totalCapacity: 55000,
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// MOVIES (10)
// ─────────────────────────────────────────────────────────────────────────────

export const movies: Content[] = [
  {
    id: 'm1',
    type: 'MOVIE',
    title: 'Kalki 2898-AD',
    description:
      'Set in a dystopian future that merges mythology with science fiction, Kalki is the 10th avatar of Vishnu, prophesied to appear at the end of the Kali Yuga to restore cosmic order.',
    image: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1200&h=500&fit=crop',
    language: 'Telugu',
    duration: 181,
    genre: 'Action / Sci-Fi',
    rating: 8.3,
    ratingCount: 124500,
    certificate: 'UA',
    cast: ['Prabhas', 'Deepika Padukone', 'Amitabh Bachchan', 'Kamal Haasan'],
    director: 'Nag Ashwin',
    releaseDate: '2026-06-27',
    isReleased: true,
    price: 250,
    trending: true,
    featured: true,
  },
  {
    id: 'm2',
    type: 'MOVIE',
    title: 'Pushpa 3: The Rise Continues',
    description:
      'The saga of Pushpa Raj continues as he tightens his grip on the red sandalwood smuggling empire while facing new threats from within and outside.',
    image: 'https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1542204165-65bf26472b9b?w=1200&h=500&fit=crop',
    language: 'Telugu',
    duration: 195,
    genre: 'Action / Drama',
    rating: 8.6,
    ratingCount: 89000,
    certificate: 'A',
    cast: ['Allu Arjun', 'Rashmika Mandanna', 'Fahadh Faasil'],
    director: 'Sukumar',
    releaseDate: '2026-08-14',
    isReleased: true,
    price: 300,
    trending: true,
    featured: true,
  },
  {
    id: 'm3',
    type: 'MOVIE',
    title: 'Jawan 2',
    description:
      'The vigilante returns in a larger, more intense chapter — uncovering corruption at the highest levels of government while protecting the people of India.',
    image: 'https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1568376794508-ae52c6ab3929?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 169,
    genre: 'Action / Thriller',
    rating: 7.9,
    ratingCount: 67000,
    certificate: 'UA',
    cast: ['Shah Rukh Khan', 'Nayanthara', 'Vijay Sethupathi'],
    director: 'Atlee',
    releaseDate: '2026-09-12',
    isReleased: true,
    price: 200,
    trending: true,
  },
  {
    id: 'm4',
    type: 'MOVIE',
    title: 'Animal Kingdom',
    description:
      'A ruthless industrialist\'s empire crumbles from within as his family turns against him. A visceral saga of power, betrayal, and survival.',
    image: 'https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1595769816263-9b910be24d5f?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 203,
    genre: 'Crime / Drama',
    rating: 7.5,
    ratingCount: 55000,
    certificate: 'A',
    cast: ['Ranbir Kapoor', 'Anil Kapoor', 'Bobby Deol'],
    director: 'Sandeep Reddy Vanga',
    releaseDate: '2026-10-02',
    isReleased: false,
    price: 250,
  },
  {
    id: 'm5',
    type: 'MOVIE',
    title: 'Singham Returns 3',
    description:
      'India\'s most fearless cop is back on the streets, taking on a politically connected crime syndicate operating across three countries.',
    image: 'https://images.unsplash.com/photo-1485846234645-a62644f84728?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1585974738771-84483dd9f89f?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 158,
    genre: 'Action',
    rating: 7.1,
    ratingCount: 43000,
    certificate: 'UA',
    cast: ['Ajay Devgn', 'Kareena Kapoor Khan', 'Deepika Padukone'],
    director: 'Rohit Shetty',
    releaseDate: '2026-11-01',
    isReleased: false,
    price: 180,
  },
  {
    id: 'm6',
    type: 'MOVIE',
    title: 'Mirzapur: The Movie',
    description:
      'The beloved gangster saga from the heartland of UP comes to the big screen — grittier, bloodier, and more politically charged than ever before.',
    image: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1542204637-e67bc7d41e48?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 175,
    genre: 'Crime / Thriller',
    rating: 8.2,
    ratingCount: 78000,
    certificate: 'A',
    cast: ['Pankaj Tripathi', 'Ali Fazal', 'Vikrant Massey'],
    director: 'Gurmmeet Singh',
    releaseDate: '2026-07-15',
    isReleased: true,
    price: 200,
    trending: true,
  },
  {
    id: 'm7',
    type: 'MOVIE',
    title: 'RRR 2',
    description:
      'The legendary duo of Ram and Bheem reunite in a new chapter of sacrifice and revolution, now fighting on a global stage for India\'s dignity.',
    image: 'https://images.unsplash.com/photo-1504512485720-7d83a16ee930?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1574267432553-4b4628081c31?w=1200&h=500&fit=crop',
    language: 'Telugu',
    duration: 188,
    genre: 'Action / Historical',
    rating: 8.8,
    ratingCount: 95000,
    certificate: 'UA',
    cast: ['Ram Charan', 'Jr. NTR', 'Alia Bhatt', 'Ajay Devgn'],
    director: 'S. S. Rajamouli',
    releaseDate: '2027-01-26',
    isReleased: false,
    price: 350,
    featured: true,
  },
  {
    id: 'm8',
    type: 'MOVIE',
    title: 'Gangubai Delhi',
    description:
      'Inspired by true events, a young woman from rural Rajasthan navigates the brutal power corridors of old Delhi to become one of its most feared and beloved figures.',
    image: 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 145,
    genre: 'Drama / Biographical',
    rating: 7.7,
    ratingCount: 34000,
    certificate: 'UA',
    cast: ['Alia Bhatt', 'Vijay Varma', 'Shefali Shah'],
    director: 'Sanjay Leela Bhansali',
    releaseDate: '2026-08-30',
    isReleased: true,
    price: 220,
  },
  {
    id: 'm9',
    type: 'MOVIE',
    title: 'Bade Miyan Chote Miyan 2',
    description:
      'India\'s most unlikely spy duo returns for a bigger, wilder, and funnier mission as a global bioterrorism threat emerges.',
    image: 'https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1544967082-d9d25d867d66?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 152,
    genre: 'Action / Comedy',
    rating: 6.8,
    ratingCount: 28000,
    certificate: 'UA',
    cast: ['Akshay Kumar', 'Tiger Shroff', 'Prithviraj Sukumaran'],
    director: 'Ali Abbas Zafar',
    releaseDate: '2026-12-25',
    isReleased: false,
    price: 200,
  },
  {
    id: 'm10',
    type: 'MOVIE',
    title: 'Stree 3',
    description:
      'The horror comedy franchise returns with a new supernatural entity terrorising the town of Chanderi — and Stree might not be the only paranormal friend this time.',
    image: 'https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=400&h=600&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1605979399824-6e2e14731af8?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 138,
    genre: 'Horror / Comedy',
    rating: 7.4,
    ratingCount: 51000,
    certificate: 'UA',
    cast: ['Rajkummar Rao', 'Shraddha Kapoor', 'Aparshakti Khurana'],
    director: 'Amar Kaushik',
    releaseDate: '2026-08-15',
    isReleased: true,
    price: 180,
    trending: true,
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// SPORTS EVENTS (8)
// ─────────────────────────────────────────────────────────────────────────────

export const sportsEvents: Content[] = [
  {
    id: 's1',
    type: 'SPORTS_MATCH',
    title: 'India vs Australia',
    description:
      'The historic Test rivalry continues as the two cricket giants battle for supremacy in a five-day clash at the iconic Wankhede Stadium.',
    image: 'https://images.unsplash.com/photo-1540747913346-19212a4d8c47?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1540747913346-19212a4d8c47?w=1200&h=500&fit=crop',
    sport: 'Cricket',
    teams: ['India', 'Australia'],
    venue: 'Wankhede Stadium',
    city: 'Mumbai',
    releaseDate: '2026-10-18',
    price: 1200,
    trending: true,
    featured: true,
    rating: 9.2,
    ratingCount: 34500,
  },
  {
    id: 's2',
    type: 'SPORTS_MATCH',
    title: 'IPL 2027: MI vs CSK',
    description:
      'The most iconic rivalry in T20 cricket. Mumbai Indians take on Chennai Super Kings in what promises to be a high-voltage encounter at DY Patil Stadium.',
    image: 'https://images.unsplash.com/photo-1594631252845-29fc4cc8cde9?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1594631252845-29fc4cc8cde9?w=1200&h=500&fit=crop',
    sport: 'Cricket',
    teams: ['Mumbai Indians', 'Chennai Super Kings'],
    venue: 'DY Patil Stadium',
    city: 'Mumbai',
    releaseDate: '2027-03-28',
    price: 800,
    trending: true,
    rating: 8.9,
    ratingCount: 56000,
  },
  {
    id: 's3',
    type: 'SPORTS_MATCH',
    title: 'Bengaluru FC vs Mumbai City FC',
    description:
      'India\'s top ISL clubs clash in a crucial league fixture. Bengaluru\'s home crowd looks to roar their team to three vital points in this derby-like encounter.',
    image: 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=1200&h=500&fit=crop',
    sport: 'Football',
    teams: ['Bengaluru FC', 'Mumbai City FC'],
    venue: 'M. Chinnaswamy Stadium',
    city: 'Bangalore',
    releaseDate: '2026-11-05',
    price: 400,
    rating: 7.6,
    ratingCount: 12000,
  },
  {
    id: 's4',
    type: 'SPORTS_MATCH',
    title: 'Badminton World Series — India Open',
    description:
      'World\'s top shuttlers compete on Indian soil. PV Sindhu leads the home challenge in what is expected to be a scintillating tournament.',
    image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?w=1200&h=500&fit=crop',
    sport: 'Badminton',
    teams: ['PV Sindhu', 'Carolina Marin', 'Tai Tzu-ying'],
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2026-12-10',
    price: 600,
    rating: 8.1,
    ratingCount: 8900,
  },
  {
    id: 's5',
    type: 'SPORTS_MATCH',
    title: 'Indian Wells Masters — Delhi Edition',
    description:
      'A special exhibition Tennis tournament bringing ATP & WTA stars to India. Watch Djokovic, Sinner, Swiatek, and more live in action.',
    image: 'https://images.unsplash.com/photo-1542144582-1ba00456b5e3?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1542144582-1ba00456b5e3?w=1200&h=500&fit=crop',
    sport: 'Tennis',
    teams: ['Novak Djokovic', 'Jannik Sinner', 'Iga Swiatek'],
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2027-01-15',
    price: 1500,
    featured: true,
    rating: 9.0,
    ratingCount: 21000,
  },
  {
    id: 's6',
    type: 'SPORTS_MATCH',
    title: 'NBA Global Game — Mumbai',
    description:
      'NBA comes to India! Two iconic NBA franchises play a pre-season exhibition game in Mumbai, marking an historic moment for Indian basketball.',
    image: 'https://images.unsplash.com/photo-1546519638-68e109498ffc?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1546519638-68e109498ffc?w=1200&h=500&fit=crop',
    sport: 'Basketball',
    teams: ['Golden State Warriors', 'Miami Heat'],
    venue: 'DY Patil Stadium',
    city: 'Mumbai',
    releaseDate: '2026-10-25',
    price: 2000,
    trending: true,
    featured: true,
    rating: 9.4,
    ratingCount: 45000,
  },
  {
    id: 's7',
    type: 'SPORTS_MATCH',
    title: 'PKL Season 12 Final',
    description:
      'The grand finale of Pro Kabaddi League Season 12. Two title contenders face off in a battle of strength, speed, and strategy.',
    image: 'https://images.unsplash.com/photo-1544551763-92ab472cad5d?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1544551763-92ab472cad5d?w=1200&h=500&fit=crop',
    sport: 'Kabaddi',
    teams: ['Jaipur Pink Panthers', 'Patna Pirates'],
    venue: 'DY Patil Stadium',
    city: 'Mumbai',
    releaseDate: '2027-02-20',
    price: 500,
    rating: 8.3,
    ratingCount: 14000,
  },
  {
    id: 's8',
    type: 'SPORTS_MATCH',
    title: 'India F1 Grand Prix 2027',
    description:
      'Formula 1 returns to India in spectacular fashion. Watch the world\'s fastest drivers battle it out on the Buddh International Circuit in Greater Noida.',
    image: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=1200&h=500&fit=crop',
    sport: 'Formula 1',
    teams: ['Max Verstappen', 'Carlos Sainz', 'Lewis Hamilton'],
    venue: 'Buddh International Circuit',
    city: 'New Delhi',
    releaseDate: '2027-02-14',
    price: 5000,
    featured: true,
    rating: 9.7,
    ratingCount: 67000,
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// CONCERTS (8)
// ─────────────────────────────────────────────────────────────────────────────

export const concerts: Content[] = [
  {
    id: 'c1',
    type: 'CONCERT',
    title: 'Arijit Singh — Aashiqui Night',
    description:
      'India\'s most beloved vocalist brings his emotional live experience to Delhi. Expect an unforgettable night of soulful melodies, heartfelt moments, and live orchestra magic.',
    image: 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=1200&h=500&fit=crop',
    artist: 'Arijit Singh',
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2026-10-24',
    duration: 180,
    price: 1999,
    trending: true,
    featured: true,
    rating: 9.5,
    ratingCount: 87000,
  },
  {
    id: 'c2',
    type: 'CONCERT',
    title: 'Diljit Dosanjh — Born to Shine Tour',
    description:
      'Diljit brings his electrifying energy and chart-topping discography to Mumbai. From Punjabi anthems to Bollywood hits — this show has it all.',
    image: 'https://images.unsplash.com/photo-1501386761578-eaa54b535def?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1505236858219-8359eb29e329?w=1200&h=500&fit=crop',
    artist: 'Diljit Dosanjh',
    venue: 'DY Patil Stadium',
    city: 'Mumbai',
    releaseDate: '2026-11-15',
    duration: 150,
    price: 2499,
    trending: true,
    rating: 9.3,
    ratingCount: 72000,
  },
  {
    id: 'c3',
    type: 'CONCERT',
    title: 'AR Rahman — Symphony of Souls',
    description:
      'The Oscar-winning maestro AR Rahman performs with a full symphony orchestra. A once-in-a-lifetime concert blending East and West in an emotional journey through his legendary compositions.',
    image: 'https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1518005020951-eccb494ad742?w=1200&h=500&fit=crop',
    artist: 'AR Rahman',
    venue: 'Palace Grounds',
    city: 'Bangalore',
    releaseDate: '2026-12-31',
    duration: 210,
    price: 3999,
    featured: true,
    rating: 9.8,
    ratingCount: 120000,
  },
  {
    id: 'c4',
    type: 'CONCERT',
    title: 'Coldplay — Music of the Spheres World Tour',
    description:
      'The legendary British rock band returns to India with their spectacular Music of the Spheres world tour featuring dazzling LED wristbands, stunning visuals, and timeless anthems.',
    image: 'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1524368535928-5b5e00ddc76b?w=1200&h=500&fit=crop',
    artist: 'Coldplay',
    venue: 'DY Patil Stadium',
    city: 'Mumbai',
    releaseDate: '2027-01-19',
    duration: 180,
    price: 5999,
    featured: true,
    trending: true,
    rating: 9.9,
    ratingCount: 245000,
  },
  {
    id: 'c5',
    type: 'CONCERT',
    title: 'Sunburn Arena — Mumbai Edition',
    description:
      'Asia\'s largest EDM festival comes to Mumbai with an all-night lineup of top international DJs including Martin Garrix, Hardwell, and Alesso.',
    image: 'https://images.unsplash.com/photo-1429962714451-bb934ecdc4ec?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1200&h=500&fit=crop',
    artist: 'Martin Garrix, Hardwell, Alesso',
    venue: 'Hiranandani Meadows',
    city: 'Mumbai',
    releaseDate: '2026-12-27',
    duration: 480,
    price: 2999,
    trending: true,
    rating: 8.7,
    ratingCount: 56000,
  },
  {
    id: 'c6',
    type: 'CONCERT',
    title: 'Shreya Ghoshal — Melodies of the Heart',
    description:
      'India\'s nightingale Shreya Ghoshal presents an evening of her most iconic songs performed live with a full orchestra — a sublime experience for music lovers.',
    image: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1506157786151-b8491531f063?w=1200&h=500&fit=crop',
    artist: 'Shreya Ghoshal',
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2026-11-08',
    duration: 180,
    price: 1499,
    rating: 9.1,
    ratingCount: 43000,
  },
  {
    id: 'c7',
    type: 'CONCERT',
    title: 'Kings of Leon — India Debut',
    description:
      'The iconic American rock band Kings of Leon makes their long-awaited Indian debut in Bangalore — bringing their raw, emotionally charged rock anthems to Indian soil for the first time.',
    image: 'https://images.unsplash.com/photo-1487537708429-7cddd6b26bdc?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=1200&h=500&fit=crop',
    artist: 'Kings of Leon',
    venue: 'Palace Grounds',
    city: 'Bangalore',
    releaseDate: '2026-10-30',
    duration: 150,
    price: 3499,
    rating: 9.0,
    ratingCount: 38000,
  },
  {
    id: 'c8',
    type: 'CONCERT',
    title: 'Nucleya — Bass Yatra 4.0',
    description:
      'Electronic music phenomenon Nucleya brings Bass Yatra to its biggest edition yet — a celebration of Indian electronic music culture with multiple stages and mind-blowing production.',
    image: 'https://images.unsplash.com/photo-1573397055557-b5ce66cd86f3?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1200&h=500&fit=crop',
    artist: 'Nucleya',
    venue: 'Palace Grounds',
    city: 'Bangalore',
    releaseDate: '2026-11-22',
    duration: 360,
    price: 1799,
    trending: true,
    rating: 8.5,
    ratingCount: 29000,
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// THEATRE EVENTS (6)
// ─────────────────────────────────────────────────────────────────────────────

export const theatreEvents: Content[] = [
  {
    id: 't1',
    type: 'THEATRE',
    title: 'Mughal-e-Azam: The Musical',
    description:
      'The legendary epic love story returns to the stage in a spectacular musical adaptation. Anarkali and Prince Salim\'s forbidden romance comes alive with stunning costumes, music, and drama.',
    image: 'https://images.unsplash.com/photo-1503095396549-807759245b35?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1503095396549-807759245b35?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 180,
    genre: 'Musical / Drama',
    venue: 'Nehru Centre Auditorium',
    city: 'Mumbai',
    releaseDate: '2026-10-10',
    price: 999,
    trending: true,
    featured: true,
    rating: 9.2,
    ratingCount: 12400,
  },
  {
    id: 't2',
    type: 'THEATRE',
    title: 'Vasu Prabhu — Stand-Up Special',
    description:
      'The acclaimed comedian delivers a brand-new hour of sharp, culturally rich stand-up comedy exploring modern India with wit, warmth, and laser-sharp observations.',
    image: 'https://images.unsplash.com/photo-1518611012118-696072aa579a?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1518611012118-696072aa579a?w=1200&h=500&fit=crop',
    language: 'English / Hindi',
    duration: 75,
    genre: 'Stand-Up Comedy',
    venue: 'Siri Fort Auditorium',
    city: 'New Delhi',
    releaseDate: '2026-10-18',
    price: 499,
    trending: true,
    rating: 8.8,
    ratingCount: 8900,
  },
  {
    id: 't3',
    type: 'THEATRE',
    title: 'A Midsummer Night\'s Dream',
    description:
      'Shakespeare\'s beloved comedy reimagined in a vibrant Indian setting by the National School of Drama. Magic, love, and laughter collide in this contemporary Indian adaptation.',
    image: 'https://images.unsplash.com/photo-1545224144-b38cd309ef69?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1545224144-b38cd309ef69?w=1200&h=500&fit=crop',
    language: 'English / Hindi',
    duration: 150,
    genre: 'Drama',
    venue: 'Siri Fort Auditorium',
    city: 'New Delhi',
    releaseDate: '2026-11-14',
    price: 799,
    rating: 8.5,
    ratingCount: 6700,
  },
  {
    id: 't4',
    type: 'THEATRE',
    title: 'Kapil Sharma — Live in Mumbai',
    description:
      'India\'s comedy king brings his electric personality to a live stage for a night of unscripted laughter, celebrity guests, and his trademark warmth.',
    image: 'https://images.unsplash.com/photo-1607113256158-56e14c8c8898?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1607113256158-56e14c8c8898?w=1200&h=500&fit=crop',
    language: 'Hindi / Punjabi',
    duration: 120,
    genre: 'Comedy',
    venue: 'Nehru Centre Auditorium',
    city: 'Mumbai',
    releaseDate: '2026-12-20',
    price: 699,
    trending: true,
    rating: 8.7,
    ratingCount: 15600,
  },
  {
    id: 't5',
    type: 'THEATRE',
    title: 'Ramayana — The Epic Retold',
    description:
      'A visually breathtaking theatrical retelling of the Ramayana combining Kathakali, Bharatanatyam, and contemporary theatre in a 3-hour immersive production.',
    image: 'https://images.unsplash.com/photo-1598387993441-a364f854cfbd?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1598387993441-a364f854cfbd?w=1200&h=500&fit=crop',
    language: 'Sanskrit / Hindi',
    duration: 210,
    genre: 'Classical Drama / Dance',
    venue: 'Nehru Centre Auditorium',
    city: 'Mumbai',
    releaseDate: '2026-11-01',
    price: 1299,
    rating: 9.4,
    ratingCount: 9800,
  },
  {
    id: 't6',
    type: 'THEATRE',
    title: 'Zakir Khan — Haq Se Single 2.0',
    description:
      'The beloved Sakht Launda returns with brand new material about life, love, and the comforting chaos of being young in modern India. Bigger venue, bigger laughs.',
    image: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=1200&h=500&fit=crop',
    language: 'Hindi',
    duration: 90,
    genre: 'Stand-Up Comedy',
    venue: 'Siri Fort Auditorium',
    city: 'New Delhi',
    releaseDate: '2026-10-22',
    price: 799,
    trending: true,
    rating: 9.0,
    ratingCount: 18900,
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// GENERAL EVENTS (10)
// ─────────────────────────────────────────────────────────────────────────────

export const generalEvents: Content[] = [
  {
    id: 'e1',
    type: 'EVENT',
    title: 'India Blockchain Summit 2026',
    description:
      'The premier tech conference on blockchain, Web3, and decentralised finance bringing together 200+ speakers, 5000+ attendees, and India\'s most promising startups.',
    image: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=1200&h=500&fit=crop',
    genre: 'Conference',
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2026-11-10',
    price: 2999,
    rating: 8.3,
    ratingCount: 4500,
  },
  {
    id: 'e2',
    type: 'EVENT',
    title: 'Lollapalooza India 2027',
    description:
      'The iconic music and arts festival returns to India for a spectacular multi-day celebration of music, art, food, and culture at Mahalaxmi Racecourse.',
    image: 'https://images.unsplash.com/photo-1472162072942-cd5147eb3902?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1472162072942-cd5147eb3902?w=1200&h=500&fit=crop',
    genre: 'Festival',
    venue: 'Mahalaxmi Racecourse',
    city: 'Mumbai',
    releaseDate: '2027-01-28',
    duration: 1440,
    price: 4999,
    trending: true,
    featured: true,
    rating: 9.6,
    ratingCount: 89000,
  },
  {
    id: 'e3',
    type: 'EVENT',
    title: 'TED×Mumbai 2026',
    description:
      'Ideas worth spreading. An independently organized TED event featuring 18 thought-provoking talks from scientists, entrepreneurs, artists, and changemakers reshaping India.',
    image: 'https://images.unsplash.com/photo-1591115765373-5207764f72e7?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1591115765373-5207764f72e7?w=1200&h=500&fit=crop',
    genre: 'Conference',
    venue: 'Nehru Centre Auditorium',
    city: 'Mumbai',
    releaseDate: '2026-10-26',
    duration: 480,
    price: 1999,
    rating: 8.9,
    ratingCount: 6700,
  },
  {
    id: 'e4',
    type: 'EVENT',
    title: 'Comic Con India 2026 — Delhi',
    description:
      'India\'s biggest pop culture event returns! Meet celebrities, artists, and creators. Exclusive merchandise, cosplay competitions, gaming tournaments, and so much more.',
    image: 'https://images.unsplash.com/photo-1533738363-b7f9aef128ce?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1533738363-b7f9aef128ce?w=1200&h=500&fit=crop',
    genre: 'Exhibition',
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2026-12-06',
    duration: 1440,
    price: 799,
    trending: true,
    rating: 8.7,
    ratingCount: 34000,
  },
  {
    id: 'e5',
    type: 'EVENT',
    title: 'Hornbill Festival 2026',
    description:
      'Experience the vibrant cultural tapestry of Nagaland at India\'s most celebrated tribal festival. Music, dance, food, sports, and the living heritage of the Naga tribes.',
    image: 'https://images.unsplash.com/photo-1571115764595-644a1f56a55c?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1571115764595-644a1f56a55c?w=1200&h=500&fit=crop',
    genre: 'Cultural Festival',
    city: 'Kohima',
    releaseDate: '2026-12-01',
    duration: 14400,
    price: 499,
    featured: true,
    rating: 9.5,
    ratingCount: 15000,
  },
  {
    id: 'e6',
    type: 'EVENT',
    title: 'Design Week Bangalore 2026',
    description:
      'Celebrating creativity and innovation — a full week of workshops, exhibitions, and talks featuring India\'s leading designers across fashion, product, digital, and architecture.',
    image: 'https://images.unsplash.com/photo-1573164713988-8665fc963095?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1573164713988-8665fc963095?w=1200&h=500&fit=crop',
    genre: 'Workshop / Exhibition',
    venue: 'Palace Grounds',
    city: 'Bangalore',
    releaseDate: '2026-11-20',
    duration: 10080,
    price: 1499,
    rating: 8.2,
    ratingCount: 3400,
  },
  {
    id: 'e7',
    type: 'EVENT',
    title: 'Startup India Summit 2026',
    description:
      'The definitive gathering for India\'s startup ecosystem. Pitch competitions, investor meets, 300+ speakers, and a showcase of 1000+ Indian startups shaping the future.',
    image: 'https://images.unsplash.com/photo-1559136555-9303baea8ebd?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1559136555-9303baea8ebd?w=1200&h=500&fit=crop',
    genre: 'Conference',
    venue: 'Jawaharlal Nehru Stadium',
    city: 'New Delhi',
    releaseDate: '2026-10-15',
    duration: 1440,
    price: 1999,
    rating: 8.6,
    ratingCount: 7800,
  },
  {
    id: 'e8',
    type: 'EVENT',
    title: 'Jaipur Literature Festival 2027',
    description:
      'The world\'s largest free literary festival brings together Nobel laureates, Booker Prize winners, and voices from across the globe in the pink city of Jaipur.',
    image: 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=1200&h=500&fit=crop',
    genre: 'Literature Festival',
    city: 'Jaipur',
    releaseDate: '2027-01-28',
    duration: 7200,
    price: 0,
    featured: true,
    rating: 9.1,
    ratingCount: 22000,
  },
  {
    id: 'e9',
    type: 'EVENT',
    title: 'Yoga Mahotsav — International Yoga Day',
    description:
      'Celebrate International Yoga Day with 10,000 practitioners on the majestic grounds of Rajpath in New Delhi. Sessions led by India\'s master yoga teachers.',
    image: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=1200&h=500&fit=crop',
    genre: 'Wellness',
    city: 'New Delhi',
    releaseDate: '2027-06-21',
    duration: 180,
    price: 0,
    rating: 8.8,
    ratingCount: 11000,
  },
  {
    id: 'e10',
    type: 'EVENT',
    title: 'Great Indian Food Festival 2026',
    description:
      'A gastronomic celebration of India\'s most beloved cuisines — 200+ food stalls, celebrity chef workshops, regional delicacies, and a food photography competition.',
    image: 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800&h=500&fit=crop',
    bannerImage: 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=1200&h=500&fit=crop',
    genre: 'Food Festival',
    venue: 'Palace Grounds',
    city: 'Bangalore',
    releaseDate: '2026-11-28',
    duration: 4320,
    price: 199,
    trending: true,
    rating: 8.9,
    ratingCount: 19000,
  },
];

// Combined for convenience
export const allEvents: Content[] = [
  ...movies,
  ...sportsEvents,
  ...concerts,
  ...theatreEvents,
  ...generalEvents,
];

// ─────────────────────────────────────────────────────────────────────────────
// SHOWS
// ─────────────────────────────────────────────────────────────────────────────

export const shows: Show[] = [
  // India vs Australia shows
  { id: 'sh1', contentId: 's1', venueId: 'v1', startTime: '2026-10-18T13:30:00', endTime: '2026-10-18T21:00:00', date: '2026-10-18', timeLabel: '1:30 PM', available: true, seatsAvailable: 4500, totalSeats: 33000 },
  { id: 'sh2', contentId: 's1', venueId: 'v1', startTime: '2026-10-19T09:30:00', endTime: '2026-10-19T18:00:00', date: '2026-10-19', timeLabel: '9:30 AM', available: true, seatsAvailable: 2800, totalSeats: 33000 },
  // Kalki shows
  { id: 'sh3', contentId: 'm1', venueId: 'v4', startTime: '2026-10-18T10:00:00', endTime: '2026-10-18T13:01:00', date: '2026-10-18', timeLabel: '10:00 AM', language: 'Telugu', format: '2D', available: true, seatsAvailable: 120, totalSeats: 350 },
  { id: 'sh4', contentId: 'm1', venueId: 'v4', startTime: '2026-10-18T14:30:00', endTime: '2026-10-18T17:31:00', date: '2026-10-18', timeLabel: '2:30 PM', language: 'Telugu', format: 'IMAX', available: true, seatsAvailable: 60, totalSeats: 250 },
  { id: 'sh5', contentId: 'm1', venueId: 'v4', startTime: '2026-10-18T19:00:00', endTime: '2026-10-18T22:01:00', date: '2026-10-18', timeLabel: '7:00 PM', language: 'Telugu', format: '3D', available: true, seatsAvailable: 40, totalSeats: 350 },
  { id: 'sh6', contentId: 'm1', venueId: 'v5', startTime: '2026-10-18T11:00:00', endTime: '2026-10-18T14:01:00', date: '2026-10-18', timeLabel: '11:00 AM', language: 'Hindi', format: '2D', available: true, seatsAvailable: 200, totalSeats: 400 },
  { id: 'sh7', contentId: 'm1', venueId: 'v5', startTime: '2026-10-18T18:00:00', endTime: '2026-10-18T21:01:00', date: '2026-10-18', timeLabel: '6:00 PM', language: 'Hindi', format: 'IMAX', available: true, seatsAvailable: 30, totalSeats: 250 },
  // Arijit Singh shows
  { id: 'sh8', contentId: 'c1', venueId: 'v2', startTime: '2026-10-24T19:00:00', endTime: '2026-10-24T22:00:00', date: '2026-10-24', timeLabel: '7:00 PM', available: true, seatsAvailable: 8000, totalSeats: 75000 },
];

// ─────────────────────────────────────────────────────────────────────────────
// MOCK BOOKINGS
// ─────────────────────────────────────────────────────────────────────────────

export const mockBookings: Booking[] = [
  {
    id: 'b1',
    bookingRef: 'EVT-2026-001245',
    userId: 'u1',
    showId: 'sh1',
    contentId: 's1',
    venueId: 'v1',
    seats: ['A4', 'A5'],
    category: 'VIP',
    ticketPrice: 2400,
    convenienceFee: 120,
    totalAmount: 2520,
    status: 'CONFIRMED',
    createdAt: '2026-09-20T10:30:00',
    content: sportsEvents[0],
    venue: venues[0],
    show: shows[0],
  },
  {
    id: 'b2',
    bookingRef: 'EVT-2026-000891',
    userId: 'u1',
    showId: 'sh3',
    contentId: 'm1',
    venueId: 'v4',
    seats: ['D6', 'D7', 'D8'],
    category: 'PREMIUM',
    ticketPrice: 750,
    convenienceFee: 50,
    totalAmount: 800,
    status: 'CONFIRMED',
    createdAt: '2026-09-15T14:20:00',
    content: movies[0],
    venue: venues[3],
    show: shows[2],
  },
  {
    id: 'b3',
    bookingRef: 'EVT-2026-000432',
    userId: 'u1',
    showId: 'sh8',
    contentId: 'c1',
    venueId: 'v2',
    seats: ['G12', 'G13'],
    category: 'PREMIUM',
    ticketPrice: 3998,
    convenienceFee: 200,
    totalAmount: 4198,
    status: 'CANCELLED',
    createdAt: '2026-09-01T09:00:00',
    content: concerts[0],
    venue: venues[1],
    show: shows[7],
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// MOCK USER
// ─────────────────────────────────────────────────────────────────────────────

export const mockUser: User = {
  id: 'u1',
  name: 'Arjun Sharma',
  email: 'arjun.sharma@email.com',
  phone: '+91 98765 43210',
  city: 'Mumbai',
  plan: 'PREMIUM',
  createdAt: '2025-03-12T08:00:00',
  savedEvents: ['c4', 's8', 'e2'],
};

// ─────────────────────────────────────────────────────────────────────────────
// SUBSCRIPTION PLANS
// ─────────────────────────────────────────────────────────────────────────────

export const plans: Plan[] = [
  {
    id: 'FREE',
    name: 'Free',
    price: 0,
    period: 'forever',
    features: [
      'Standard booking access',
      'Up to 4 tickets per booking',
      'Email confirmation',
      'Standard support',
    ],
    limits: [
      { plan: 'FREE', route: '/api/events', limit: 30, window: 'per minute' },
      { plan: 'FREE', route: '/api/search', limit: 10, window: 'per minute' },
      { plan: 'FREE', route: '/api/bookings', limit: 5, window: 'per minute' },
    ],
  },
  {
    id: 'PREMIUM',
    name: 'Premium',
    price: 199,
    period: 'month',
    highlighted: true,
    features: [
      'Priority booking access',
      'Up to 8 tickets per booking',
      'SMS + Email confirmation',
      'Early access to popular events',
      'Priority customer support',
      'Exclusive member-only offers',
      'No booking fees on select events',
    ],
    limits: [
      { plan: 'PREMIUM', route: '/api/events', limit: 100, window: 'per minute' },
      { plan: 'PREMIUM', route: '/api/search', limit: 50, window: 'per minute' },
      { plan: 'PREMIUM', route: '/api/bookings', limit: 20, window: 'per minute' },
    ],
  },
  {
    id: 'VIP',
    name: 'VIP',
    price: 499,
    period: 'month',
    features: [
      'Highest priority booking',
      'Unlimited tickets per booking',
      'Dedicated concierge support',
      'First access to all events',
      'VIP-only exclusive events',
      'Zero convenience fees',
      'Complimentary seat upgrades',
      'Personal event calendar',
    ],
    limits: [
      { plan: 'VIP', route: '/api/events', limit: 500, window: 'per minute' },
      { plan: 'VIP', route: '/api/search', limit: 100, window: 'per minute' },
      { plan: 'VIP', route: '/api/bookings', limit: 60, window: 'per minute' },
    ],
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// RATE LIMIT POLICIES
// ─────────────────────────────────────────────────────────────────────────────

export const rateLimitPolicies: RateLimitPolicy[] = [
  { plan: 'FREE', route: 'GET /api/events', limit: 30, window: '1 min' },
  { plan: 'FREE', route: 'GET /api/search', limit: 10, window: '1 min' },
  { plan: 'FREE', route: 'POST /api/bookings', limit: 5, window: '1 min' },
  { plan: 'FREE', route: 'GET /api/seats', limit: 15, window: '1 min' },
  { plan: 'PREMIUM', route: 'GET /api/events', limit: 100, window: '1 min' },
  { plan: 'PREMIUM', route: 'GET /api/search', limit: 50, window: '1 min' },
  { plan: 'PREMIUM', route: 'POST /api/bookings', limit: 20, window: '1 min' },
  { plan: 'PREMIUM', route: 'GET /api/seats', limit: 60, window: '1 min' },
  { plan: 'VIP', route: 'GET /api/events', limit: 500, window: '1 min' },
  { plan: 'VIP', route: 'GET /api/search', limit: 100, window: '1 min' },
  { plan: 'VIP', route: 'POST /api/bookings', limit: 60, window: '1 min' },
  { plan: 'VIP', route: 'GET /api/seats', limit: 200, window: '1 min' },
];

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN STATS
// ─────────────────────────────────────────────────────────────────────────────

export const adminStats: AdminStats = {
  totalUsers: 124800,
  totalBookings: 89420,
  todayRevenue: 2847500,
  activeEvents: 342,
  bookingsTrend: [
    { date: 'Sep 15', count: 820 },
    { date: 'Sep 16', count: 940 },
    { date: 'Sep 17', count: 780 },
    { date: 'Sep 18', count: 1120 },
    { date: 'Sep 19', count: 1380 },
    { date: 'Sep 20', count: 1050 },
    { date: 'Sep 21', count: 1240 },
    { date: 'Sep 22', count: 1680 },
  ],
  revenueTrend: [
    { date: 'Sep 15', amount: 1840000 },
    { date: 'Sep 16', amount: 2100000 },
    { date: 'Sep 17', amount: 1750000 },
    { date: 'Sep 18', amount: 2520000 },
    { date: 'Sep 19', amount: 3100000 },
    { date: 'Sep 20', amount: 2360000 },
    { date: 'Sep 21', amount: 2790000 },
    { date: 'Sep 22', amount: 2847500 },
  ],
  bookingsByCategory: [
    { category: 'Movies', count: 38400 },
    { category: 'Sports', count: 22100 },
    { category: 'Concerts', count: 18900 },
    { category: 'Theatre', count: 6200 },
    { category: 'Events', count: 3820 },
  ],
};

// ─────────────────────────────────────────────────────────────────────────────
// CITIES
// ─────────────────────────────────────────────────────────────────────────────

export const cities = [
  'Mumbai', 'New Delhi', 'Bangalore', 'Hyderabad', 'Chennai',
  'Kolkata', 'Pune', 'Chandigarh', 'Ahmedabad', 'Jaipur', 'Kochi',
];

// ─────────────────────────────────────────────────────────────────────────────
// SEAT MAP GENERATOR
// ─────────────────────────────────────────────────────────────────────────────

export function generateSeatSections(showId: string): SeatSection[] {
  const vipRows = ['A', 'B'];
  const premiumRows = ['C', 'D', 'E', 'F'];
  const regularRows = ['G', 'H', 'I', 'J', 'K'];

  // Deterministic "booked" seats based on showId hash
  const bookedSeeds = showId.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);

  const makeRow = (row: string, category: 'VIP' | 'PREMIUM' | 'REGULAR', seatsPerRow: number, basePrice: number): SeatRow => {
    const seats: Seat[] = Array.from({ length: seatsPerRow }, (_, i) => {
      const num = i + 1;
      const label = `${row}${num}`;
      const seedVal = (bookedSeeds + row.charCodeAt(0) + num) % 10;
      const status: import('../types').SeatStatus =
        seedVal < 3 ? 'BOOKED' : seedVal === 3 ? 'UNAVAILABLE' : 'AVAILABLE';
      return { id: `${showId}-${label}`, showId, row, number: num, label, category, price: basePrice, status };
    });
    return { row, category, seats };
  };

  return [
    {
      category: 'VIP',
      price: 1200,
      rows: vipRows.map(r => makeRow(r, 'VIP', 12, 1200)),
    },
    {
      category: 'PREMIUM',
      price: 800,
      rows: premiumRows.map(r => makeRow(r, 'PREMIUM', 14, 800)),
    },
    {
      category: 'REGULAR',
      price: 500,
      rows: regularRows.map(r => makeRow(r, 'REGULAR', 16, 500)),
    },
  ];
}

// ─────────────────────────────────────────────────────────────────────────────
// POPULAR / RECENT SEARCHES
// ─────────────────────────────────────────────────────────────────────────────

export const popularSearches = [
  'Coldplay', 'India vs Australia', 'Kalki 2898', 'Arijit Singh',
  'IPL 2027', 'Comic Con', 'Lollapalooza', 'RRR 2',
];

export const recentSearches = [
  'Mumbai concerts', 'Cricket tickets', 'AR Rahman',
];

